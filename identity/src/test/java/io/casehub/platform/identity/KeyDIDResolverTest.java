package io.casehub.platform.identity;

import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.VerificationMethod;
import org.junit.jupiter.api.Test;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class KeyDIDResolverTest {

    private final KeyDIDResolver resolver = new KeyDIDResolver();

    @Test
    void returns_empty_for_null_did() {
        assertTrue(resolver.resolve("actor", null).isEmpty());
    }

    @Test
    void returns_empty_for_non_key_did() {
        assertTrue(resolver.resolve("actor", "did:web:example.com").isEmpty());
    }

    @Test
    void resolved_publicKeyBytes_are_spki_format() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] spki = keyPair.getPublic().getEncoded();

        // Build a did:key: z + base64url(multicodec_prefix + raw_key)
        byte[] raw = new byte[32];
        System.arraycopy(spki, spki.length - 32, raw, 0, 32);
        byte[] multicodec = new byte[2 + raw.length];
        multicodec[0] = (byte) 0xed;
        multicodec[1] = 0x01;
        System.arraycopy(raw, 0, multicodec, 2, raw.length);
        String didKey = "did:key:z" + Base64.getUrlEncoder().withoutPadding().encodeToString(multicodec);

        DIDDocument doc = resolver.resolve("actor", didKey).orElseThrow();
        assertEquals(1, doc.verificationMethods().size());

        VerificationMethod vm = doc.verificationMethods().get(0);
        byte[] vmBytes = vm.publicKeyBytes();

        // Must be SPKI format (44 bytes for Ed25519), not raw (32 bytes)
        assertEquals(44, vmBytes.length);

        // Must be loadable as a public key via X509EncodedKeySpec
        var loadedKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(vmBytes));
        assertNotNull(loadedKey);
    }
}
