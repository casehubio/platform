package io.casehub.platform.signing;

import io.casehub.platform.api.signing.SignatureResult;
import io.casehub.platform.api.signing.SigningKeyMaterial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NoOpSigningProviderTest {

    private NoOpSigningProvider provider;

    @BeforeEach
    void setUp() {
        provider = new NoOpSigningProvider();
    }

    @Test
    void sign_shouldReturnEmpty() {
        Optional<SignatureResult> result = provider.sign("actor1", "data".getBytes());
        assertTrue(result.isEmpty());
    }

    @Test
    void sign_shouldReturnEmptyForDifferentActors() {
        assertTrue(provider.sign("actor1", "data".getBytes()).isEmpty());
        assertTrue(provider.sign("actor2", "data".getBytes()).isEmpty());
    }

    @Test
    void keyMaterial_shouldReturnEmpty() {
        Optional<SigningKeyMaterial> result = provider.keyMaterial("actor1");
        assertTrue(result.isEmpty());
    }

    @Test
    void sign_shouldThrowOnNullActorId() {
        assertThrows(NullPointerException.class,
                () -> provider.sign(null, "data".getBytes()));
    }

    @Test
    void sign_shouldThrowOnNullData() {
        assertThrows(NullPointerException.class,
                () -> provider.sign("actor1", null));
    }

    @Test
    void sign_shouldDeduplicateWarnings() {
        provider.sign("actor1", "data1".getBytes());
        provider.sign("actor1", "data2".getBytes());
    }

    @Test
    void sign_shouldTrackMultipleActors() {
        provider.sign("actor1", "data".getBytes());
        provider.sign("actor2", "data".getBytes());
        provider.sign("actor1", "data".getBytes());
    }
}
