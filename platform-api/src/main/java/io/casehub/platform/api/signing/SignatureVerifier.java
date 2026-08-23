package io.casehub.platform.api.signing;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;

/**
 * Algorithm-transparent signature verification utility.
 *
 * <p>Detects the signing algorithm from the public key's X.509
 * SubjectPublicKeyInfo DER metadata — callers never specify an algorithm.
 * Supports Ed25519, EC (P-256, P-384, P-521 via ECDSA), and ML-DSA
 * (when BouncyCastle >= 1.79 is present).
 *
 * <p>The algorithm list is tried in order — Ed25519 first (current default),
 * then EC, then ML-DSA variants. Placing the most common algorithm first
 * minimizes trial-and-error exception overhead under load.
 *
 * <p>Never throws. Returns a {@link VerificationOutcome} that indicates
 * success or the specific failure mode.
 *
 * <p>Composes naturally with the identity module's {@code VerificationMethod}
 * record, which stores public keys in the same SPKI format:
 * <pre>{@code
 * VerificationMethod vm = didDocument.verificationMethods().get(0);
 * VerificationOutcome outcome = SignatureVerifier.verify(
 *         data, signature, vm.publicKeyBytes());
 * }</pre>
 * The identity module's {@code AgentIdentityVerificationService} handles
 * key-matching (is this key in the DID document?); {@code SignatureVerifier}
 * handles signature verification (is this signature valid for this key?).
 */
public final class SignatureVerifier {

    private static final List<String> SUPPORTED_ALGORITHMS =
            List.of("Ed25519", "EC", "ML-DSA-44", "ML-DSA-65", "ML-DSA-87");

    private SignatureVerifier() {}

    /**
     * Verify a signature against the given data and public key.
     *
     * @param data      the original signed bytes
     * @param signature the signature to verify
     * @param publicKey X.509 SubjectPublicKeyInfo DER-encoded public key
     * @return verification outcome — check {@code == VALID} for binary semantics
     */
    public static VerificationOutcome verify(final byte[] data, final byte[] signature,
            final byte[] publicKey) {
        if (data == null || signature == null || publicKey == null
                || data.length == 0 || signature.length == 0 || publicKey.length == 0) {
            return VerificationOutcome.INVALID_INPUT;
        }
        final PublicKey pub;
        try {
            pub = loadPublicKey(publicKey);
        } catch (final java.security.InvalidKeyException e) {
            return VerificationOutcome.UNSUPPORTED_ALGORITHM;
        } catch (final Exception e) {
            return VerificationOutcome.MALFORMED_KEY;
        }
        try {
            final Signature sig = Signature.getInstance(signatureAlgorithm(pub));
            sig.initVerify(pub);
            sig.update(data);
            return sig.verify(signature)
                    ? VerificationOutcome.VALID
                    : VerificationOutcome.SIGNATURE_MISMATCH;
        } catch (final IllegalArgumentException e) {
            return VerificationOutcome.UNSUPPORTED_ALGORITHM;
        } catch (final Exception e) {
            return VerificationOutcome.MALFORMED_KEY;
        }
    }

    private static PublicKey loadPublicKey(final byte[] encoded)
            throws java.security.InvalidKeyException {
        final X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
        for (final String algo : SUPPORTED_ALGORITHMS) {
            try {
                return KeyFactory.getInstance(algo).generatePublic(spec);
            } catch (final java.security.NoSuchAlgorithmException
                    | java.security.spec.InvalidKeySpecException ignored) {
            }
        }
        throw new java.security.InvalidKeyException(
                "Public key bytes do not match any supported algorithm");
    }

    private static String signatureAlgorithm(final java.security.Key key) {
        if (!"EC".equals(key.getAlgorithm())) {
            return key.getAlgorithm();
        }
        final ECKey ec = (ECKey) key;
        return switch (ec.getParams().getOrder().bitLength()) {
            case 256 -> "SHA256withECDSA";
            case 384 -> "SHA384withECDSA";
            case 521 -> "SHA512withECDSA";
            default -> throw new IllegalArgumentException(
                    "Unsupported EC curve order: " + ec.getParams().getOrder().bitLength());
        };
    }
}
