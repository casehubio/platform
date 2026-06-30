package io.casehub.platform.identity;

import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.DIDMethod;
import io.casehub.platform.api.identity.DIDResolver;
import io.casehub.platform.api.identity.VerificationMethod;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Resolves did:key DIDs by decoding key material directly from the DID string.
 * Standards note: the real did:key spec uses base58btc multibase encoding.
 * This implementation uses base64url for consistency with Java's standard library.
 *
 * <p>The raw key bytes from the did:key multicodec encoding are wrapped in SPKI
 * (X.509 SubjectPublicKeyInfo) format before being stored in {@link VerificationMethod},
 * consistent with the platform SPKI convention.
 *
 * <p>Does NOT produce alsoKnownAs entries — did:key documents are deterministic
 * from key bytes only.
 */
@ApplicationScoped
@DIDMethod
@Priority(100)
public class KeyDIDResolver implements DIDResolver {

    private static final Logger LOG = Logger.getLogger(KeyDIDResolver.class);
    private static final String DID_KEY_PREFIX = "did:key:";
    private static final byte[] ED25519_SPKI_PREFIX = {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    @Override
    public Optional<DIDDocument> resolve(final String actorId, final String did) {
        if (did == null || !did.startsWith(DID_KEY_PREFIX)) return Optional.empty();
        try {
            final String keyPart = did.substring(DID_KEY_PREFIX.length());
            if (!keyPart.startsWith("z")) return Optional.empty();
            final byte[] multicodec = Base64.getUrlDecoder().decode(keyPart.substring(1));
            if (multicodec.length < 2) return Optional.empty();
            final byte[] rawKey = new byte[multicodec.length - 2];
            System.arraycopy(multicodec, 2, rawKey, 0, rawKey.length);
            final byte[] spkiBytes = wrapEd25519Spki(rawKey);
            final String vmId = did + "#" + keyPart;
            final var vm = new VerificationMethod(vmId, "Ed25519VerificationKey2020", spkiBytes);
            return Optional.of(new DIDDocument(did, List.of(vm), List.of()));
        } catch (final Exception e) {
            LOG.debugf("KeyDIDResolver: failed to decode %s: %s", did, e.getMessage());
            return Optional.empty();
        }
    }

    private static byte[] wrapEd25519Spki(final byte[] rawKey) {
        final byte[] spki = new byte[ED25519_SPKI_PREFIX.length + rawKey.length];
        System.arraycopy(ED25519_SPKI_PREFIX, 0, spki, 0, ED25519_SPKI_PREFIX.length);
        System.arraycopy(rawKey, 0, spki, ED25519_SPKI_PREFIX.length, rawKey.length);
        return spki;
    }
}
