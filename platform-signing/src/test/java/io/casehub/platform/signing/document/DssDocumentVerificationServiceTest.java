package io.casehub.platform.signing.document;

import io.casehub.platform.api.signing.document.SigningIdentity;
import io.casehub.platform.api.signing.document.SigningProfile;
import io.casehub.platform.api.signing.document.VerificationStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DssDocumentVerificationServiceTest {

    @TempDir
    static Path tempDir;
    static DssDocumentSigningService signer;
    static DssDocumentVerificationService verifier;

    @BeforeAll
    static void setup() throws Exception {
        Path ks = TestKeyStoreHelper.createTestKeystore(tempDir);
        var mgr = new KeyStoreManager(
                ks.toString(), TestKeyStoreHelper.PASSWORD,
                "PKCS12", TestKeyStoreHelper.ALIAS);
        signer = new DssDocumentSigningService(mgr, SigningProfile.B_B, null);
        verifier = new DssDocumentVerificationService();
    }

    @Test
    void verifyPdf_signedPdf_returnsValid() throws Exception {
        byte[] pdf = TestKeyStoreHelper.createMinimalPdf();
        var signed = signer.signPdf(pdf, new SigningIdentity("a", "t")).orElseThrow();
        var result = verifier.verifyPdf(signed.signedBytes());
        assertThat(result.status()).isEqualTo(VerificationStatus.VALID);
        assertThat(result.signerDn()).contains("CN=Test Seal");
        assertThat(result.certificateChain()).isNotEmpty();
    }

    @Test
    void verifyPdf_unsignedPdf_returnsUnsigned() throws Exception {
        byte[] pdf = TestKeyStoreHelper.createMinimalPdf();
        var result = verifier.verifyPdf(pdf);
        assertThat(result.status()).isEqualTo(VerificationStatus.UNSIGNED);
    }

    @Test
    void verifyPdf_tamperedPdf_returnsInvalid() throws Exception {
        byte[] pdf = TestKeyStoreHelper.createMinimalPdf();
        var signed = signer.signPdf(pdf, new SigningIdentity("a", "t")).orElseThrow();
        byte[] tampered = signed.signedBytes().clone();
        // Flip a byte well inside the content but before the signature
        int offset = Math.min(100, tampered.length / 2);
        tampered[offset] ^= 0xFF;
        var result = verifier.verifyPdf(tampered);
        assertThat(result.status()).isIn(VerificationStatus.INVALID, VerificationStatus.ERROR);
    }

    @Test
    void verifyDetached_validSignature_returnsValid() {
        byte[] data = "test compliance report content".getBytes();
        var sig = signer.signDetached(data, new SigningIdentity("a", "t")).orElseThrow();
        var result = verifier.verifyDetached(data, sig.signatureBytes());
        assertThat(result.status()).isEqualTo(VerificationStatus.VALID);
        assertThat(result.signerDn()).contains("CN=Test Seal");
    }

    @Test
    void verifyDetached_tamperedData_returnsInvalid() {
        byte[] data = "test compliance report content".getBytes();
        var sig = signer.signDetached(data, new SigningIdentity("a", "t")).orElseThrow();
        var result = verifier.verifyDetached("modified content".getBytes(), sig.signatureBytes());
        assertThat(result.status()).isEqualTo(VerificationStatus.INVALID);
    }

    @Test
    void verifyPdf_garbage_returnsError() {
        var result = verifier.verifyPdf(new byte[]{1, 2, 3, 4, 5});
        assertThat(result.status()).isIn(VerificationStatus.ERROR, VerificationStatus.UNSIGNED);
    }

    @Test
    void verifyDetached_garbage_returnsError() {
        var result = verifier.verifyDetached("data".getBytes(), new byte[]{1, 2, 3});
        assertThat(result.status()).isIn(VerificationStatus.ERROR, VerificationStatus.INVALID);
    }
}
