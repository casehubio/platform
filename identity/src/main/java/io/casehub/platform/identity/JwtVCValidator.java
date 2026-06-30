package io.casehub.platform.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.identity.AgentCredentialValidator;
import io.casehub.platform.api.identity.CredentialValidationResult;
import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.DIDResolver;
import io.casehub.platform.api.identity.VerificationMethod;
import io.casehub.platform.identity.config.IdentityConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * W3C VC JWT credential validator.
 *
 * <p>Validates Verifiable Credentials secured as JWTs (JOSE). Reads credential
 * files from paths configured via {@code casehub.identity.credentials."actorId"}.
 * Supports EdDSA (Ed25519) and ES256 (P-256) signature algorithms.
 *
 * <p>{@code @ApplicationScoped} — displaces {@link NoOpCredentialValidator @DefaultBean}
 * when this class is on the classpath.
 */
@ApplicationScoped
public class JwtVCValidator extends AbstractCachingIdentityProvider<CredentialValidationResult>
        implements AgentCredentialValidator {

    private static final Logger LOG = Logger.getLogger(JwtVCValidator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final byte[] ED25519_X509_PREFIX = {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private final Map<String, String> credentials;
    private final DIDResolver resolver;

    JwtVCValidator() {
        super(Duration.ofMinutes(5));
        this.credentials = Map.of();
        this.resolver = (actorId, did) -> Optional.empty();
    }

    @Inject
    public JwtVCValidator(final IdentityConfig config, final DIDResolver resolver) {
        this(config.credentials(), resolver,
                Duration.ofMinutes(config.credentialCacheTtlMinutes()));
    }

    JwtVCValidator(final Map<String, String> credentials,
                   final DIDResolver resolver,
                   final Duration cacheTtl) {
        super(cacheTtl);
        this.credentials = credentials;
        this.resolver = resolver;
    }

    @Override
    public Optional<CredentialValidationResult> validate(final String actorId, final String did) {
        if (!credentials.containsKey(actorId)) {
            return Optional.empty();
        }
        final String cacheKey = actorId + "|" + did;
        final Optional<CredentialValidationResult> cached = get(cacheKey);
        if (cached.isPresent() && cached.get() == CredentialValidationResult.EXPIRED) {
            invalidate(cacheKey);
            return Optional.of(loadAndValidate(actorId, did));
        }
        if (cached.isPresent()) {
            return cached;
        }
        final CredentialValidationResult result = loadAndValidate(actorId, did);
        if (result != CredentialValidationResult.EXPIRED) {
            put(cacheKey, Optional.of(result));
        }
        return Optional.of(result);
    }

    @Override
    protected Optional<CredentialValidationResult> loadContext(final String key) {
        return Optional.empty();
    }

    private CredentialValidationResult loadAndValidate(final String actorId, final String did) {
        final String filePath = credentials.get(actorId);
        final String jwt;
        try {
            jwt = Files.readString(Path.of(filePath)).trim();
        } catch (final Exception e) {
            LOG.debugf("JwtVCValidator: cannot read credential file for %s at %s: %s",
                    actorId, filePath, e.getMessage());
            return CredentialValidationResult.NOT_FOUND;
        }

        final String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            LOG.debugf("JwtVCValidator: malformed JWT for %s — expected 3 parts, got %d",
                    actorId, parts.length);
            return CredentialValidationResult.INVALID_SIGNATURE;
        }

        try {
            final JsonNode header = MAPPER.readTree(base64urlDecode(parts[0]));
            final JsonNode payload = MAPPER.readTree(base64urlDecode(parts[1]));

            final String alg = header.path("alg").asText("");
            final String issuerDid = payload.path("iss").asText("");
            final long exp = payload.path("exp").asLong(0);
            final String subjectId = payload.path("vc").path("credentialSubject").path("id").asText("");

            if (exp > 0 && Instant.ofEpochSecond(exp).isBefore(Instant.now())) {
                return CredentialValidationResult.EXPIRED;
            }

            if (!subjectId.equals(did)) {
                return CredentialValidationResult.INVALID_SIGNATURE;
            }

            final Optional<DIDDocument> issuerDoc = resolver.resolve(null, issuerDid);
            if (issuerDoc.isEmpty()) {
                return CredentialValidationResult.ISSUER_UNKNOWN;
            }

            final Optional<VerificationMethod> vm = findMatchingKey(issuerDoc.get(), alg);
            if (vm.isEmpty()) {
                return CredentialValidationResult.ISSUER_UNKNOWN;
            }

            final String signingInput = parts[0] + "." + parts[1];
            final byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);

            if (!verifySignature(alg, vm.get().publicKeyBytes(), signingInput.getBytes(), signatureBytes)) {
                return CredentialValidationResult.INVALID_SIGNATURE;
            }

            return CredentialValidationResult.VALID;
        } catch (final Exception e) {
            LOG.debugf("JwtVCValidator: validation failed for %s: %s", actorId, e.getMessage());
            return CredentialValidationResult.INVALID_SIGNATURE;
        }
    }

    private static Optional<VerificationMethod> findMatchingKey(final DIDDocument doc, final String alg) {
        final String expectedType = switch (alg) {
            case "EdDSA" -> "Ed25519VerificationKey2020";
            case "ES256" -> "EcdsaSecp256r1VerificationKey2019";
            default -> "";
        };
        if (expectedType.isEmpty()) return Optional.empty();
        return doc.verificationMethods().stream()
                .filter(vm -> expectedType.equals(vm.type()))
                .findFirst();
    }

    private static boolean verifySignature(final String alg, final byte[] rawPublicKey,
                                           final byte[] data, final byte[] signatureBytes) throws Exception {
        return switch (alg) {
            case "EdDSA" -> verifyEd25519(rawPublicKey, data, signatureBytes);
            case "ES256" -> verifyES256(rawPublicKey, data, signatureBytes);
            default -> false;
        };
    }

    private static boolean verifyEd25519(final byte[] rawPublicKey, final byte[] data,
                                         final byte[] signatureBytes) throws Exception {
        final byte[] x509Encoded = new byte[ED25519_X509_PREFIX.length + rawPublicKey.length];
        System.arraycopy(ED25519_X509_PREFIX, 0, x509Encoded, 0, ED25519_X509_PREFIX.length);
        System.arraycopy(rawPublicKey, 0, x509Encoded, ED25519_X509_PREFIX.length, rawPublicKey.length);
        final PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(x509Encoded));
        final Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signatureBytes);
    }

    private static boolean verifyES256(final byte[] rawPublicKey, final byte[] data,
                                       final byte[] signatureBytes) throws Exception {
        // Raw 65-byte uncompressed point (0x04 || x || y) or 64-byte (x || y)
        final byte[] uncompressed;
        if (rawPublicKey.length == 64) {
            uncompressed = new byte[65];
            uncompressed[0] = 0x04;
            System.arraycopy(rawPublicKey, 0, uncompressed, 1, 64);
        } else {
            uncompressed = rawPublicKey;
        }
        // Build X.509 SubjectPublicKeyInfo for EC P-256
        final byte[] x509Encoded = buildEcX509(uncompressed);
        final PublicKey publicKey = KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(x509Encoded));
        final Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signatureBytes);
    }

    private static byte[] buildEcX509(final byte[] uncompressedPoint) {
        // ASN.1 DER: SEQUENCE { SEQUENCE { OID ecPublicKey, OID prime256v1 }, BIT STRING { point } }
        final byte[] oidEc = {0x06, 0x07, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x02, 0x01};
        final byte[] oidP256 = {0x06, 0x08, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x03, 0x01, 0x07};
        final byte[] algSeq = asn1Sequence(concat(oidEc, oidP256));
        final byte[] bitContent = new byte[1 + uncompressedPoint.length];
        System.arraycopy(uncompressedPoint, 0, bitContent, 1, uncompressedPoint.length);
        final byte[] fullBitString = new byte[2 + bitContent.length];
        fullBitString[0] = 0x03;
        fullBitString[1] = (byte) bitContent.length;
        System.arraycopy(bitContent, 0, fullBitString, 2, bitContent.length);
        return asn1Sequence(concat(algSeq, fullBitString));
    }

    private static byte[] asn1Sequence(final byte[] content) {
        final byte[] seq = new byte[2 + content.length];
        seq[0] = 0x30;
        seq[1] = (byte) content.length;
        System.arraycopy(content, 0, seq, 2, content.length);
        return seq;
    }

    private static byte[] concat(final byte[] a, final byte[] b) {
        final byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static byte[] base64urlDecode(final String encoded) {
        return Base64.getUrlDecoder().decode(encoded);
    }
}
