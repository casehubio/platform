package io.casehub.platform.authn.webauthn;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.AuthenticationProvider;
import io.casehub.platform.api.authn.AuthenticationResult;
import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.ChallengeResponse;

import io.casehub.platform.api.authn.InvalidChallengeException;
import io.casehub.platform.api.authn.InvalidCredentialException;
import io.casehub.platform.api.authn.UserResolver;
import io.casehub.platform.api.authn.WebAuthnCredential;
import io.casehub.platform.api.authn.WebAuthnCredentialStore;
import io.casehub.platform.api.identity.PrincipalId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;

import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WebAuthnAuthenticationProvider implements AuthenticationProvider {

    private static final String METHOD = "webauthn";
    private static final String FLOW_REGISTER = "register";
    private static final CBORFactory CBOR_FACTORY = new CBORFactory();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebAuthnConfig config;
    private final WebAuthnCredentialStore credentialStore;
    private final UserResolver userResolver;
    private final ConcurrentHashMap<String, InitiationContext> pendingChallenges = new ConcurrentHashMap<>();

    private record InitiationContext(byte[] nonce, String actorId) {}

    public WebAuthnAuthenticationProvider(WebAuthnConfig config,
                                          WebAuthnCredentialStore credentialStore,
                                          UserResolver userResolver) {
        this.config = Objects.requireNonNull(config, "config");
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.userResolver = Objects.requireNonNull(userResolver, "userResolver");
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    public ChallengeResponse initiate(AuthenticationContext context) {
        String flow = (String) context.hints().getOrDefault("flow", "authenticate");
        byte[] challengeBytes = generateChallenge();
        String challengeB64 = base64UrlEncode(challengeBytes);
        String challengeId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(config.challengeTimeoutSeconds());

        String actorId = context.existingPrincipal()
                .map(PrincipalId::id)
                .orElse(null);
        pendingChallenges.put(challengeId, new InitiationContext(challengeBytes, actorId));

        Map<String, Object> options;
        if (FLOW_REGISTER.equals(flow)) {
            options = buildRegistrationOptions(context, challengeB64);
        } else {
            options = buildAssertionOptions(context, challengeB64);
        }

        return new WebAuthnChallengeResponse(challengeId, expiresAt, options);
    }

    @Override
    public AuthenticationResult verify(ChallengeRecord challenge, Map<String, Object> data) {
        var ctx = pendingChallenges.remove(challenge.challengeId());
        byte[] expectedNonce = ctx != null ? ctx.nonce() : null;

        if (data.containsKey("attestationObject")) {
            return verifyRegistration(challenge, data, expectedNonce, ctx);
        } else {
            return verifyAuthentication(challenge, data, expectedNonce);
        }
    }

    private Map<String, Object> buildRegistrationOptions(AuthenticationContext context, String challengeB64) {
        var options = new LinkedHashMap<String, Object>();
        options.put("challenge", challengeB64);
        options.put("rp", Map.of("id", config.rpId(), "name", config.rpName()));

        String actorId = context.existingPrincipal()
                .map(PrincipalId::id)
                .orElse("anonymous");
        options.put("user", Map.of(
                "id", base64UrlEncode(actorId.getBytes(StandardCharsets.UTF_8)),
                "name", actorId,
                "displayName", actorId));
        options.put("pubKeyCredParams", List.of(
                Map.of("type", "public-key", "alg", -7),
                Map.of("type", "public-key", "alg", -8)));
        options.put("timeout", config.challengeTimeoutSeconds() * 1000);
        options.put("attestation", "none");
        options.put("authenticatorSelection", Map.of(
                "residentKey", "preferred",
                "userVerification", "preferred"));

        var existing = context.existingPrincipal()
                .map(p -> credentialStore.findByActorId(p.id(), context.tenancyId()))
                .orElse(List.of());
        if (!existing.isEmpty()) {
            options.put("excludeCredentials", existing.stream()
                    .map(c -> {
                        var m = new LinkedHashMap<String, Object>();
                        m.put("type", "public-key");
                        m.put("id", c.credentialId());
                        if (!c.transports().isEmpty()) {
                            m.put("transports", List.copyOf(c.transports()));
                        }
                        return m;
                    })
                    .toList());
        }

        return options;
    }

    private Map<String, Object> buildAssertionOptions(AuthenticationContext context, String challengeB64) {
        var options = new LinkedHashMap<String, Object>();
        options.put("challenge", challengeB64);
        options.put("rpId", config.rpId());
        options.put("timeout", config.challengeTimeoutSeconds() * 1000);
        options.put("userVerification", "preferred");

        String actorId = (String) context.hints().get("actorId");
        if (actorId != null) {
            var credentials = credentialStore.findByActorId(actorId, context.tenancyId());
            if (!credentials.isEmpty()) {
                options.put("allowCredentials", credentials.stream()
                        .map(c -> {
                            var m = new LinkedHashMap<String, Object>();
                            m.put("type", "public-key");
                            m.put("id", c.credentialId());
                            if (!c.transports().isEmpty()) {
                                m.put("transports", List.copyOf(c.transports()));
                            }
                            return m;
                        })
                        .toList());
            }
        }

        return options;
    }

    private AuthenticationResult verifyRegistration(ChallengeRecord challenge, Map<String, Object> data,
                                                     byte[] expectedNonce, InitiationContext ctx) {
        byte[] clientDataJson = base64UrlDecode((String) data.get("clientDataJSON"));
        validateClientData(clientDataJson, "webauthn.create", expectedNonce);

        byte[] attestationObjectBytes = base64UrlDecode((String) data.get("attestationObject"));
        var attestation = parseAttestationObject(attestationObjectBytes);
        byte[] authDataBytes = (byte[]) attestation.get("authData");
        var authData = AuthenticatorDataParser.parse(authDataBytes);

        validateRpIdHash(authData.rpIdHash());
        validateUserPresent(authData);

        if (!authData.hasAttestedCredentialData() || authData.attestedCredentialData() == null) {
            throw new InvalidChallengeException(METHOD, "No attested credential data in registration response");
        }

        var attested = authData.attestedCredentialData();
        String actorId = ctx != null && ctx.actorId() != null ? ctx.actorId() : "anonymous";

        String aaguidStr = formatAaguid(attested.aaguid());
        var credential = new WebAuthnCredential(
                base64UrlEncode(attested.credentialId()),
                actorId,
                challenge.tenancyId(),
                attested.credentialPublicKeyCose(),
                authData.signCount(),
                Set.of(),
                aaguidStr,
                "Passkey",
                Instant.now(),
                null,
                true);
        credentialStore.store(credential);

        return new AuthenticationResult(
                PrincipalId.human(actorId),
                challenge.tenancyId(),
                Set.of(),
                METHOD,
                Map.of("credentialId", credential.credentialId()));
    }

    private AuthenticationResult verifyAuthentication(ChallengeRecord challenge, Map<String, Object> data,
                                                       byte[] expectedNonce) {
        byte[] clientDataJson = base64UrlDecode((String) data.get("clientDataJSON"));
        validateClientData(clientDataJson, "webauthn.get", expectedNonce);

        byte[] authDataBytes = base64UrlDecode((String) data.get("authenticatorData"));
        byte[] signatureBytes = base64UrlDecode((String) data.get("signature"));
        String credentialId = (String) data.get("credentialId");

        var authData = AuthenticatorDataParser.parse(authDataBytes);
        validateRpIdHash(authData.rpIdHash());
        validateUserPresent(authData);

        var credential = credentialStore.findByCredentialId(credentialId)
                .orElseThrow(() -> new InvalidCredentialException(METHOD,
                        "Credential not found: " + credentialId));

        if (credential.signCount() > 0 && authData.signCount() <= credential.signCount()) {
            throw new InvalidCredentialException(METHOD,
                    "Possible cloned authenticator — sign count regressed: " +
                            authData.signCount() + " <= " + credential.signCount());
        }

        PublicKey publicKey = CoseKeyParser.parse(credential.publicKeyCose());
        String algorithm = CoseKeyParser.algorithmName(credential.publicKeyCose());

        byte[] clientDataHash = sha256(clientDataJson);
        byte[] signedData = new byte[authDataBytes.length + clientDataHash.length];
        System.arraycopy(authDataBytes, 0, signedData, 0, authDataBytes.length);
        System.arraycopy(clientDataHash, 0, signedData, authDataBytes.length, clientDataHash.length);

        if (!verifySignature(publicKey, algorithm, signedData, signatureBytes)) {
            throw new InvalidCredentialException(METHOD, "Invalid signature");
        }

        credentialStore.updateAfterAuthentication(credentialId, authData.signCount(), Instant.now());

        return new AuthenticationResult(
                PrincipalId.human(credential.actorId()),
                credential.tenancyId(),
                Set.of(),
                METHOD,
                Map.of("credentialId", credentialId));
    }

    private void validateClientData(byte[] clientDataJson, String expectedType, byte[] expectedNonce) {
        try {
            JsonNode node = JSON.readTree(clientDataJson);
            String type = node.path("type").asText();
            if (!expectedType.equals(type)) {
                throw new InvalidChallengeException(METHOD,
                        "Unexpected client data type: " + type + ", expected " + expectedType);
            }

            String challengeB64 = node.path("challenge").asText();
            byte[] challengeBytes = base64UrlDecode(challengeB64);
            if (expectedNonce != null && !MessageDigest.isEqual(challengeBytes, expectedNonce)) {
                throw new InvalidChallengeException(METHOD, "challenge mismatch in client data");
            }

            String origin = node.path("origin").asText();
            if (!config.allowedOrigins().contains(origin)) {
                throw new InvalidChallengeException(METHOD,
                        "Unexpected origin: " + origin);
            }
        } catch (IOException e) {
            throw new InvalidChallengeException(METHOD, "Invalid client data JSON: " + e.getMessage());
        }
    }

    private void validateRpIdHash(byte[] rpIdHash) {
        byte[] expected = sha256(config.rpId().getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(rpIdHash, expected)) {
            throw new InvalidChallengeException(METHOD, "RP ID hash mismatch");
        }
    }

    private void validateUserPresent(AuthenticatorData authData) {
        if (!authData.userPresent()) {
            throw new InvalidChallengeException(METHOD, "User presence flag not set");
        }
    }

    private Map<String, Object> parseAttestationObject(byte[] attestationBytes) {
        try (var parser = CBOR_FACTORY.createParser(attestationBytes)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new InvalidChallengeException(METHOD, "Invalid attestation object: not a CBOR map");
            }
            Map<String, Object> result = new HashMap<>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();
                if ("authData".equals(fieldName)) {
                    result.put("authData", parser.getBinaryValue());
                } else if ("fmt".equals(fieldName)) {
                    result.put("fmt", parser.getText());
                } else {
                    parser.skipChildren();
                }
            }
            return result;
        } catch (IOException e) {
            throw new InvalidChallengeException(METHOD, "Invalid attestation object CBOR: " + e.getMessage());
        }
    }

    private boolean verifySignature(PublicKey publicKey, String algorithm, byte[] data, byte[] signature) {
        try {
            var sig = Signature.getInstance(algorithm);
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] generateChallenge() {
        byte[] challenge = new byte[config.challengeLength()];
        new java.security.SecureRandom().nextBytes(challenge);
        return challenge;
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String formatAaguid(byte[] aaguid) {
        if (aaguid == null || aaguid.length != 16) return null;
        var sb = new StringBuilder(36);
        for (int i = 0; i < 16; i++) {
            sb.append(String.format("%02x", aaguid[i]));
            if (i == 3 || i == 5 || i == 7 || i == 9) sb.append('-');
        }
        return sb.toString();
    }
}
