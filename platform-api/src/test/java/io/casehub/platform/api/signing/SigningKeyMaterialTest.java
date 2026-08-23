package io.casehub.platform.api.signing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SigningKeyMaterialTest {

    private static final byte[] PUB = {5, 6, 7, 8};

    @Test
    void constructor_shouldRejectNullPublicKey() {
        assertThrows(NullPointerException.class,
                () -> new SigningKeyMaterial(null));
    }

    @Test
    void constructor_shouldRejectEmptyPublicKey() {
        assertThrows(IllegalArgumentException.class,
                () -> new SigningKeyMaterial(new byte[0]));
    }

    @Test
    void constructor_shouldComputeKeyRef() {
        SigningKeyMaterial material = new SigningKeyMaterial(PUB);

        assertNotNull(material.keyRef());
        assertEquals(SignatureResult.computeKeyRef(PUB), material.keyRef());
    }

    @Test
    void defensiveCopy_shouldPreventMutationViaConstructorInput() {
        byte[] pub = {4, 5, 6};
        SigningKeyMaterial material = new SigningKeyMaterial(pub);

        pub[0] = 99;

        assertEquals(4, material.publicKey()[0]);
    }

    @Test
    void defensiveCopy_shouldPreventMutationViaAccessor() {
        SigningKeyMaterial material = new SigningKeyMaterial(PUB);

        material.publicKey()[0] = 99;

        assertEquals(5, material.publicKey()[0]);
    }

    @Test
    void equals_shouldCompareByContent() {
        SigningKeyMaterial a = new SigningKeyMaterial(PUB.clone());
        SigningKeyMaterial b = new SigningKeyMaterial(PUB.clone());

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_shouldDetectDifference() {
        SigningKeyMaterial a = new SigningKeyMaterial(PUB);
        SigningKeyMaterial b = new SigningKeyMaterial(new byte[]{99});

        assertNotEquals(a, b);
    }

    @Test
    void toString_shouldRedactBytes() {
        SigningKeyMaterial material = new SigningKeyMaterial(PUB);
        String str = material.toString();

        assertTrue(str.contains("keyRef="));
        assertTrue(str.contains("<4 bytes>"));
    }
}
