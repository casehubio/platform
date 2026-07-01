package io.casehub.platform.identity;

import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.DIDMethod;
import io.casehub.platform.api.identity.DIDResolver;
import io.casehub.platform.api.identity.VerificationMethod;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Resolves did:key DIDs by decoding key material directly from the DID string.
 * Standards note: the real did:key spec uses base58btc multibase encoding.
 * This implementation uses base64url for consistency with Java's standard library.
 *
 * <p>Supports Ed25519 (multicodec 0xed) and P-256 (multicodec 0x1200) key types.
 * The multicodec prefix is decoded as an unsigned LEB128 varint.
 *
 * <p>The raw key bytes from the did:key multicodec encoding are converted to SPKI
 * (X.509 SubjectPublicKeyInfo) format before being stored in {@link VerificationMethod},
 * consistent with the platform SPKI convention. For P-256, compressed SEC1 points
 * are decompressed to canonical 91-byte uncompressed SPKI.
 *
 * <p>Populates {@code alsoKnownAs} with the provided actorId when non-null.
 */
@ApplicationScoped
@DIDMethod
@Priority(100)
public class KeyDIDResolver implements DIDResolver {

    private static final Logger LOG = Logger.getLogger(KeyDIDResolver.class);
    private static final String DID_KEY_PREFIX = "did:key:";

    @Override
    public Optional<DIDDocument> resolve(final String actorId, final String did) {
        if (did == null || !did.startsWith(DID_KEY_PREFIX)) return Optional.empty();
        try {
            final String keyPart = did.substring(DID_KEY_PREFIX.length());
            if (!keyPart.startsWith("z")) return Optional.empty();
            final byte[] multicodec = Base64.getUrlDecoder().decode(keyPart.substring(1));

            final int[] varint = decodeVarint(multicodec);
            if (varint == null) return Optional.empty();

            final Optional<MulticodecKeyType> keyType = MulticodecKeyType.fromCode(varint[0]);
            if (keyType.isEmpty()) return Optional.empty();

            final MulticodecKeyType type = keyType.get();
            final byte[] rawKey = Arrays.copyOfRange(multicodec, varint[1], multicodec.length);
            if (rawKey.length != type.rawKeyLength) return Optional.empty();

            final byte[] spki = type.toSpki(rawKey);
            final String vmId = did + "#" + keyPart;
            final var vm = new VerificationMethod(vmId, type.vmType, spki);
            final var aka = actorId != null ? List.of(actorId) : List.<String>of();
            return Optional.of(new DIDDocument(did, List.of(vm), aka));
        } catch (final Exception e) {
            LOG.debugf("KeyDIDResolver: failed to decode %s: %s", did, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Decodes an unsigned LEB128 varint from the start of a byte array.
     *
     * @return {@code int[]{value, bytesConsumed}} or {@code null} if the varint is truncated
     */
    private static int[] decodeVarint(final byte[] data) {
        if (data == null || data.length == 0) return null;
        int value = 0;
        int shift = 0;
        for (int i = 0; i < Math.min(data.length, 4); i++) {
            int b = data[i] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return new int[]{value, i + 1};
            }
            shift += 7;
        }
        return null;
    }
}
