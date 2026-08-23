package io.casehub.platform.api.signing;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;

import static org.junit.jupiter.api.Assertions.*;

class SignatureVerifierTest {

    @Test
    void verify_ed25519RoundTrip() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = kpg.generateKeyPair();
        byte[] data = "test data for signing".getBytes();

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(data);
        byte[] signature = signer.sign();

        VerificationOutcome outcome = SignatureVerifier.verify(
                data, signature, keyPair.getPublic().getEncoded());

        assertEquals(VerificationOutcome.VALID, outcome);
    }

    @Test
    void verify_ecP256RoundTrip() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair keyPair = kpg.generateKeyPair();
        byte[] data = "test data for EC signing".getBytes();

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(data);
        byte[] signature = signer.sign();

        VerificationOutcome outcome = SignatureVerifier.verify(
                data, signature, keyPair.getPublic().getEncoded());

        assertEquals(VerificationOutcome.VALID, outcome);
    }

    @Test
    void verify_tamperedData_shouldReturnMismatch() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = kpg.generateKeyPair();
        byte[] data = "original data".getBytes();

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(data);
        byte[] signature = signer.sign();

        byte[] tampered = "tampered data".getBytes();
        VerificationOutcome outcome = SignatureVerifier.verify(
                tampered, signature, keyPair.getPublic().getEncoded());

        assertEquals(VerificationOutcome.SIGNATURE_MISMATCH, outcome);
    }

    @Test
    void verify_malformedPublicKey_shouldReturnMalformedOrUnsupported() {
        byte[] data = "test".getBytes();
        byte[] sig = {1, 2, 3};
        byte[] badKey = {0, 0, 0, 0, 0};

        VerificationOutcome outcome = SignatureVerifier.verify(data, sig, badKey);

        assertTrue(outcome == VerificationOutcome.MALFORMED_KEY
                        || outcome == VerificationOutcome.UNSUPPORTED_ALGORITHM,
                "Expected MALFORMED_KEY or UNSUPPORTED_ALGORITHM, got " + outcome);
    }

    @Test
    void verify_nullData_shouldReturnInvalidInput() {
        assertEquals(VerificationOutcome.INVALID_INPUT,
                SignatureVerifier.verify(null, new byte[]{1}, new byte[]{2}));
    }

    @Test
    void verify_nullSignature_shouldReturnInvalidInput() {
        assertEquals(VerificationOutcome.INVALID_INPUT,
                SignatureVerifier.verify(new byte[]{1}, null, new byte[]{2}));
    }

    @Test
    void verify_nullPublicKey_shouldReturnInvalidInput() {
        assertEquals(VerificationOutcome.INVALID_INPUT,
                SignatureVerifier.verify(new byte[]{1}, new byte[]{2}, null));
    }

    @Test
    void verify_emptyData_shouldReturnInvalidInput() {
        assertEquals(VerificationOutcome.INVALID_INPUT,
                SignatureVerifier.verify(new byte[0], new byte[]{1}, new byte[]{2}));
    }

    @Test
    void verify_emptySignature_shouldReturnInvalidInput() {
        assertEquals(VerificationOutcome.INVALID_INPUT,
                SignatureVerifier.verify(new byte[]{1}, new byte[0], new byte[]{2}));
    }

    @Test
    void verify_emptyPublicKey_shouldReturnInvalidInput() {
        assertEquals(VerificationOutcome.INVALID_INPUT,
                SignatureVerifier.verify(new byte[]{1}, new byte[]{2}, new byte[0]));
    }
}
