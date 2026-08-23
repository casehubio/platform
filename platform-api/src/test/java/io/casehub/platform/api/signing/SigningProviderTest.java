package io.casehub.platform.api.signing;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SigningProviderTest {

    @Test
    void keyMaterial_defaultMethod_shouldExtractFromSignResult() {
        byte[] pub = {4, 5, 6};
        SigningProvider provider = (actorId, data) -> Optional.of(
                new SignatureResult(new byte[]{1, 2, 3}, pub));

        Optional<SigningKeyMaterial> km = provider.keyMaterial("test");

        assertTrue(km.isPresent());
        assertArrayEquals(pub, km.get().publicKey());
        assertEquals(SignatureResult.computeKeyRef(pub), km.get().keyRef());
    }

    @Test
    void keyMaterial_defaultMethod_shouldReturnEmptyWhenSignReturnsEmpty() {
        SigningProvider provider = (actorId, data) -> Optional.empty();

        Optional<SigningKeyMaterial> km = provider.keyMaterial("test");

        assertTrue(km.isEmpty());
    }
}
