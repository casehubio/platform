package io.casehub.platform.authn.webauthn;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.InvalidChallengeException;
import io.casehub.platform.api.authn.InvalidCredentialException;
import io.casehub.platform.api.authn.WebAuthnCredential;
import io.casehub.platform.api.authn.WebAuthnCredentialStore;
import io.casehub.platform.api.authn.UserResolver;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebAuthnAuthenticationProviderTest {

    private static final String RP_ID = "example.com";
    private static final String RP_NAME = "Example";
    private static final String ORIGIN = "https://example.com";
    private static final String TENANT = "tenant-1";

    private InMemWebAuthnCredentialStore credentialStore;
    private StubUserResolver userResolver;
    private WebAuthnAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        credentialStore = new InMemWebAuthnCredentialStore();
        userResolver = new StubUserResolver();
        var config = new TestWebAuthnConfig(RP_ID, RP_NAME, Set.of(ORIGIN));
        provider = new WebAuthnAuthenticationProvider(config, credentialStore, userResolver);
    }

    @Test
    void methodReturnsWebauthn() {
        assertThat(provider.method()).isEqualTo("webauthn");
    }

    @Nested
    class Registration {

        @Test
        void initiateReturnsRegistrationOptions() {
            var context = registrationContext("user-1");
            var response = provider.initiate(context);

            assertThat(response).isInstanceOf(WebAuthnChallengeResponse.class);
            var webAuthnResponse = (WebAuthnChallengeResponse) response;
            var options = webAuthnResponse.options();
            assertThat(options).containsKey("challenge");
            assertThat(options).containsKey("rp");
            assertThat(options).containsKey("user");
            assertThat(options).containsKey("pubKeyCredParams");
        }

        @Test
        void initiateIncludesRpInfo() {
            var context = registrationContext("user-1");
            var response = (WebAuthnChallengeResponse) provider.initiate(context);

            @SuppressWarnings("unchecked")
            var rp = (Map<String, Object>) response.options().get("rp");
            assertThat(rp.get("id")).isEqualTo(RP_ID);
            assertThat(rp.get("name")).isEqualTo(RP_NAME);
        }

        @Test
        void initiateExcludesExistingCredentials() {
            var existing = new WebAuthnCredential(
                    "existing-cred", "user-1", TENANT, new byte[32], 0,
                    Set.of(), null, "Key 1", Instant.now(), null, false);
            credentialStore.store(existing);

            var context = registrationContext("user-1");
            var response = (WebAuthnChallengeResponse) provider.initiate(context);

            @SuppressWarnings("unchecked")
            var excludeCredentials = (List<Map<String, Object>>) response.options().get("excludeCredentials");
            assertThat(excludeCredentials).isNotNull().hasSize(1);
            assertThat(excludeCredentials.get(0).get("id")).isEqualTo("existing-cred");
        }

        @Test
        void verifyRegistrationStoresCredential() throws Exception {
            var kp = generateEcKeyPair();
            var context = registrationContext("user-1");
            var response = (WebAuthnChallengeResponse) provider.initiate(context);
            String challengeB64 = (String) response.options().get("challenge");

            var challenge = challengeRecord(response);
            var data = buildRegistrationData(kp, challengeB64, ORIGIN, RP_ID);

            var result = provider.verify(challenge, data);

            assertThat(result.principal()).isEqualTo(PrincipalId.human("user-1"));
            assertThat(result.method()).isEqualTo("webauthn");
            assertThat(credentialStore.stored).hasSize(1);
        }

        @Test
        void verifyRegistrationRejectsWrongOrigin() throws Exception {
            var kp = generateEcKeyPair();
            var context = registrationContext("user-1");
            var response = (WebAuthnChallengeResponse) provider.initiate(context);
            String challengeB64 = (String) response.options().get("challenge");

            var challenge = challengeRecord(response);
            var data = buildRegistrationData(kp, challengeB64, "https://evil.com", RP_ID);

            assertThatThrownBy(() -> provider.verify(challenge, data))
                    .isInstanceOf(InvalidChallengeException.class)
                    .hasMessageContaining("origin");
        }

        @Test
        void verifyRegistrationRejectsWrongRpId() throws Exception {
            var kp = generateEcKeyPair();
            var context = registrationContext("user-1");
            var response = (WebAuthnChallengeResponse) provider.initiate(context);
            String challengeB64 = (String) response.options().get("challenge");

            var challenge = challengeRecord(response);
            var data = buildRegistrationData(kp, challengeB64, ORIGIN, "evil.com");

            assertThatThrownBy(() -> provider.verify(challenge, data))
                    .isInstanceOf(InvalidChallengeException.class)
                    .hasMessageContaining("RP ID");
        }

        @Test
        void verifyRegistrationRejectsWrongChallenge() throws Exception {
            var kp = generateEcKeyPair();
            var context = registrationContext("user-1");
            var response = (WebAuthnChallengeResponse) provider.initiate(context);

            var challenge = challengeRecord(response);
            var data = buildRegistrationData(kp, base64Url(new byte[32]), ORIGIN, RP_ID);

            assertThatThrownBy(() -> provider.verify(challenge, data))
                    .isInstanceOf(InvalidChallengeException.class)
                    .hasMessageContaining("challenge");
        }

        @Test
        void verifyRegistrationRejectsWrongType() throws Exception {
            var kp = generateEcKeyPair();
            var context = registrationContext("user-1");
            var response = (WebAuthnChallengeResponse) provider.initiate(context);
            String challengeB64 = (String) response.options().get("challenge");

            var challenge = challengeRecord(response);
            var clientDataJson = buildClientDataJson("webauthn.get", challengeB64, ORIGIN);
            byte[] attestationObject = buildAttestationObject(kp, RP_ID);
            var data = Map.<String, Object>of(
                    "clientDataJSON", base64Url(clientDataJson),
                    "attestationObject", base64Url(attestationObject));

            assertThatThrownBy(() -> provider.verify(challenge, data))
                    .isInstanceOf(InvalidChallengeException.class)
                    .hasMessageContaining("type");
        }
    }

    @Nested
    class Authentication {

        @Test
        void initiateReturnsAssertionOptions() {
            storeCredential("cred-1", "user-1");
            var context = authenticationContext();
            var response = (WebAuthnChallengeResponse) provider.initiate(context);

            var options = response.options();
            assertThat(options).containsKey("challenge");
            assertThat(options.get("rpId")).isEqualTo(RP_ID);
            @SuppressWarnings("unchecked")
            var allowCredentials = (List<Map<String, Object>>) options.get("allowCredentials");
            assertThat(allowCredentials).isNull();
        }

        @Test
        void initiateWithHintIncludesAllowedCredentials() {
            storeCredential("cred-1", "user-1");
            var context = new AuthenticationContext(
                    "webauthn", TENANT, ORIGIN, Optional.empty(),
                    Map.of("flow", "authenticate", "actorId", "user-1"));
            var response = (WebAuthnChallengeResponse) provider.initiate(context);

            @SuppressWarnings("unchecked")
            var allowCredentials = (List<Map<String, Object>>) response.options().get("allowCredentials");
            assertThat(allowCredentials).hasSize(1);
            assertThat(allowCredentials.get(0).get("id")).isEqualTo("cred-1");
        }

        @Test
        void verifyAuthenticationSucceeds() throws Exception {
            var kp = generateEcKeyPair();
            byte[] coseKey = buildCoseKeyFromEc(kp);
            var credential = new WebAuthnCredential(
                    "cred-1", "user-1", TENANT, coseKey, 0,
                    Set.of(), null, "Key 1", Instant.now(), null, true);
            credentialStore.store(credential);

            var context = authenticationContext();
            var response = (WebAuthnChallengeResponse) provider.initiate(context);
            String challengeB64 = (String) response.options().get("challenge");

            var challenge = challengeRecord(response);
            var data = buildAuthenticationData(kp, challengeB64, ORIGIN, RP_ID, "cred-1", 1);

            var result = provider.verify(challenge, data);

            assertThat(result.principal()).isEqualTo(PrincipalId.human("user-1"));
            assertThat(result.method()).isEqualTo("webauthn");

            var updated = credentialStore.stored.get("cred-1");
            assertThat(updated.signCount()).isEqualTo(1);
        }

        @Test
        void verifyAuthenticationRejectsUnknownCredential() throws Exception {
            var kp = generateEcKeyPair();
            var context = authenticationContext();
            var response = (WebAuthnChallengeResponse) provider.initiate(context);
            String challengeB64 = (String) response.options().get("challenge");

            var challenge = challengeRecord(response);
            var data = buildAuthenticationData(kp, challengeB64, ORIGIN, RP_ID, "unknown-cred", 1);

            assertThatThrownBy(() -> provider.verify(challenge, data))
                    .isInstanceOf(InvalidCredentialException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        void verifyAuthenticationRejectsInvalidSignature() throws Exception {
            var kp = generateEcKeyPair();
            byte[] coseKey = buildCoseKeyFromEc(kp);
            var credential = new WebAuthnCredential(
                    "cred-1", "user-1", TENANT, coseKey, 0,
                    Set.of(), null, "Key 1", Instant.now(), null, true);
            credentialStore.store(credential);

            var differentKp = generateEcKeyPair();
            var context = authenticationContext();
            var response = (WebAuthnChallengeResponse) provider.initiate(context);
            String challengeB64 = (String) response.options().get("challenge");

            var challenge = challengeRecord(response);
            var data = buildAuthenticationData(differentKp, challengeB64, ORIGIN, RP_ID, "cred-1", 1);

            assertThatThrownBy(() -> provider.verify(challenge, data))
                    .isInstanceOf(InvalidCredentialException.class)
                    .hasMessageContaining("signature");
        }

        @Test
        void verifyAuthenticationRejectsRegressedSignCount() throws Exception {
            var kp = generateEcKeyPair();
            byte[] coseKey = buildCoseKeyFromEc(kp);
            var credential = new WebAuthnCredential(
                    "cred-1", "user-1", TENANT, coseKey, 10,
                    Set.of(), null, "Key 1", Instant.now(), null, true);
            credentialStore.store(credential);

            var context = authenticationContext();
            var response = (WebAuthnChallengeResponse) provider.initiate(context);
            String challengeB64 = (String) response.options().get("challenge");

            var challenge = challengeRecord(response);
            var data = buildAuthenticationData(kp, challengeB64, ORIGIN, RP_ID, "cred-1", 5);

            assertThatThrownBy(() -> provider.verify(challenge, data))
                    .isInstanceOf(InvalidCredentialException.class)
                    .hasMessageContaining("sign count");
        }
    }

    // --- helpers ---

    private AuthenticationContext registrationContext(String actorId) {
        return new AuthenticationContext(
                "webauthn", TENANT, ORIGIN,
                Optional.of(PrincipalId.human(actorId)),
                Map.of("flow", "register"));
    }

    private AuthenticationContext authenticationContext() {
        return new AuthenticationContext(
                "webauthn", TENANT, ORIGIN, Optional.empty(),
                Map.of("flow", "authenticate"));
    }

    private ChallengeRecord challengeRecord(WebAuthnChallengeResponse response) {
        return new ChallengeRecord(
                response.challengeId(), "webauthn", TENANT,
                null, Instant.now(), response.expiresAt());
    }

    private void storeCredential(String credId, String actorId) {
        credentialStore.store(new WebAuthnCredential(
                credId, actorId, TENANT, new byte[32], 0,
                Set.of(), null, "Key", Instant.now(), null, false));
    }

    private static KeyPair generateEcKeyPair() throws Exception {
        var kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        return kpg.generateKeyPair();
    }

    private static byte[] buildCoseKeyFromEc(KeyPair kp) throws Exception {
        var ecPub = (ECPublicKey) kp.getPublic();
        byte[] x = toUnsignedFixedLength(ecPub.getW().getAffineX().toByteArray(), 32);
        byte[] y = toUnsignedFixedLength(ecPub.getW().getAffineY().toByteArray(), 32);
        return CoseKeyParserTest.buildCoseEc2Key(-7, 1, x, y);
    }

    private static Map<String, Object> buildRegistrationData(KeyPair kp, String challengeB64,
                                                              String origin, String rpId) throws Exception {
        byte[] clientDataJson = buildClientDataJson("webauthn.create", challengeB64, origin);
        byte[] attestationObject = buildAttestationObject(kp, rpId);
        return Map.of(
                "clientDataJSON", base64Url(clientDataJson),
                "attestationObject", base64Url(attestationObject));
    }

    private static Map<String, Object> buildAuthenticationData(KeyPair kp, String challengeB64,
                                                                String origin, String rpId,
                                                                String credentialId,
                                                                long signCount) throws Exception {
        byte[] clientDataJson = buildClientDataJson("webauthn.get", challengeB64, origin);
        byte[] clientDataHash = sha256(clientDataJson);

        byte[] rpIdHash = sha256(rpId.getBytes());
        byte flags = (byte) (AuthenticatorData.FLAG_UP | AuthenticatorData.FLAG_UV);
        byte[] authData = ByteBuffer.allocate(37)
                .put(rpIdHash).put(flags).putInt((int) signCount).array();

        byte[] signedData = new byte[authData.length + clientDataHash.length];
        System.arraycopy(authData, 0, signedData, 0, authData.length);
        System.arraycopy(clientDataHash, 0, signedData, authData.length, clientDataHash.length);

        var sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(kp.getPrivate());
        sig.update(signedData);
        byte[] signature = sig.sign();

        return Map.of(
                "clientDataJSON", base64Url(clientDataJson),
                "authenticatorData", base64Url(authData),
                "signature", base64Url(signature),
                "credentialId", credentialId);
    }

    private static byte[] buildClientDataJson(String type, String challenge, String origin) {
        return ("{\"type\":\"" + type + "\",\"challenge\":\"" + challenge
                + "\",\"origin\":\"" + origin + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] buildAttestationObject(KeyPair kp, String rpId) throws Exception {
        var ecPub = (ECPublicKey) kp.getPublic();
        byte[] x = toUnsignedFixedLength(ecPub.getW().getAffineX().toByteArray(), 32);
        byte[] y = toUnsignedFixedLength(ecPub.getW().getAffineY().toByteArray(), 32);
        byte[] coseKey = CoseKeyParserTest.buildCoseEc2Key(-7, 1, x, y);

        byte[] credentialId = "test-credential-id".getBytes();
        byte[] aaguid = new byte[16];

        byte[] rpIdHash = sha256(rpId.getBytes());
        byte flags = (byte) (AuthenticatorData.FLAG_UP | AuthenticatorData.FLAG_UV | AuthenticatorData.FLAG_AT);

        int authDataLen = 37 + 16 + 2 + credentialId.length + coseKey.length;
        var authData = ByteBuffer.allocate(authDataLen);
        authData.put(rpIdHash);
        authData.put(flags);
        authData.putInt(0); // signCount
        authData.put(aaguid);
        authData.putShort((short) credentialId.length);
        authData.put(credentialId);
        authData.put(coseKey);

        var bos = new ByteArrayOutputStream();
        var factory = new CBORFactory();
        try (var gen = factory.createGenerator(bos)) {
            gen.writeStartObject();
            gen.writeStringField("fmt", "none");
            gen.writeFieldName("attStmt");
            gen.writeStartObject();
            gen.writeEndObject();
            gen.writeFieldName("authData");
            gen.writeBinary(authData.array());
            gen.writeEndObject();
        }
        return bos.toByteArray();
    }

    private static byte[] sha256(byte[] input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }

    private static byte[] sha256(String input) throws Exception {
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] toUnsignedFixedLength(byte[] input, int length) {
        if (input.length == length) return input;
        byte[] result = new byte[length];
        if (input.length > length) {
            System.arraycopy(input, input.length - length, result, 0, length);
        } else {
            System.arraycopy(input, 0, result, length - input.length, input.length);
        }
        return result;
    }

    // --- stubs ---

    private record TestWebAuthnConfig(String rpId, String rpName, Set<String> allowedOrigins) implements WebAuthnConfig {}

    private static class StubUserResolver implements UserResolver {
        @Override
        public Optional<PrincipalId> resolveByEmail(String email, String tenancyId) {
            return Optional.empty();
        }
    }

    private static class InMemWebAuthnCredentialStore implements WebAuthnCredentialStore {
        final ConcurrentHashMap<String, WebAuthnCredential> stored = new ConcurrentHashMap<>();

        @Override
        public void store(WebAuthnCredential credential) {
            stored.put(credential.credentialId(), credential);
        }

        @Override
        public Optional<WebAuthnCredential> findByCredentialId(String credentialId) {
            return Optional.ofNullable(stored.get(credentialId));
        }

        @Override
        public List<WebAuthnCredential> findByActorId(String actorId, String tenancyId) {
            return stored.values().stream()
                    .filter(c -> c.actorId().equals(actorId) && c.tenancyId().equals(tenancyId))
                    .toList();
        }

        @Override
        public void updateAfterAuthentication(String credentialId, long newSignCount, Instant lastUsedAt) {
            stored.computeIfPresent(credentialId, (k, old) ->
                    new WebAuthnCredential(old.credentialId(), old.actorId(), old.tenancyId(),
                            old.publicKeyCose(), newSignCount, old.transports(), old.aaguid(),
                            old.displayName(), old.createdAt(), lastUsedAt, old.discoverable()));
        }

        @Override
        public void delete(String credentialId) {
            stored.remove(credentialId);
        }
    }
}
