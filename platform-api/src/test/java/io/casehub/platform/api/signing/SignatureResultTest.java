package io.casehub.platform.api.signing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignatureResultTest {

    private static final byte[] SIG = {1, 2, 3, 4};
    private static final byte[] PUB = {5, 6, 7, 8};

    @Test
    void constructor_shouldRejectNullSignature() {
        assertThrows(NullPointerException.class,
                () -> new SignatureResult(null, PUB));
    }

    @Test
    void constructor_shouldRejectNullPublicKey() {
        assertThrows(NullPointerException.class,
                () -> new SignatureResult(SIG, null));
    }

    @Test
    void constructor_shouldRejectEmptySignature() {
        assertThrows(IllegalArgumentException.class,
                () -> new SignatureResult(new byte[0], PUB));
    }

    @Test
    void constructor_shouldRejectEmptyPublicKey() {
        assertThrows(IllegalArgumentException.class,
                () -> new SignatureResult(SIG, new byte[0]));
    }

    @Test
    void constructor_shouldComputeKeyRef() {
        SignatureResult result = new SignatureResult(SIG, PUB);

        assertNotNull(result.keyRef());
        assertEquals(SignatureResult.computeKeyRef(PUB), result.keyRef());
    }

    @Test
    void defensiveCopy_shouldPreventMutationViaConstructorInput() {
        byte[] sig = {1, 2, 3};
        byte[] pub = {4, 5, 6};
        SignatureResult result = new SignatureResult(sig, pub);

        sig[0] = 99;
        pub[0] = 99;

        assertEquals(1, result.signature()[0]);
        assertEquals(4, result.publicKey()[0]);
    }

    @Test
    void defensiveCopy_shouldPreventMutationViaAccessor() {
        SignatureResult result = new SignatureResult(SIG, PUB);

        result.signature()[0] = 99;
        result.publicKey()[0] = 99;

        assertEquals(1, result.signature()[0]);
        assertEquals(5, result.publicKey()[0]);
    }

    @Test
    void equals_shouldCompareByContent() {
        SignatureResult a = new SignatureResult(SIG.clone(), PUB.clone());
        SignatureResult b = new SignatureResult(SIG.clone(), PUB.clone());

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_shouldDetectDifference() {
        SignatureResult a = new SignatureResult(SIG, PUB);
        SignatureResult b = new SignatureResult(new byte[]{99}, PUB);

        assertNotEquals(a, b);
    }

    @Test
    void toString_shouldRedactBytes() {
        SignatureResult result = new SignatureResult(SIG, PUB);
        String str = result.toString();

        assertTrue(str.contains("keyRef="));
        assertTrue(str.contains("<4 bytes>"));
        assertFalse(str.contains("[1, 2, 3, 4]"));
    }

    @Test
    void computeKeyRef_shouldBeDeterministic() {
        byte[] key = {10, 20, 30, 40};
        String ref1 = SignatureResult.computeKeyRef(key);
        String ref2 = SignatureResult.computeKeyRef(key.clone());

        assertNotNull(ref1);
        assertFalse(ref1.isEmpty());
        assertEquals(ref1, ref2);
    }

    @Test
    void computeKeyRef_shouldProduceBase64UrlWithNoPadding() {
        byte[] key = {10, 20, 30, 40};
        String ref = SignatureResult.computeKeyRef(key);

        assertFalse(ref.contains("="), "no padding");
        assertFalse(ref.contains("+"), "no + (Base64URL uses -)");
        assertFalse(ref.contains("/"), "no / (Base64URL uses _)");
    }

    @Test
    void computeKeyRef_shouldMatchKnownAnswer() {
        byte[] key = {10, 20, 30, 40};
        assertEquals("X1PA_we6XZozDmjJXauxqbxJ4p-e1T9vp8bZmrsAAFA",
                SignatureResult.computeKeyRef(key));
    }

    @Test
    void computeKeyRef_shouldRejectNull() {
        assertThrows(NullPointerException.class,
                () -> SignatureResult.computeKeyRef(null));
    }
}
