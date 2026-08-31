package io.casehub.platform.signing.document;

import io.casehub.platform.api.signing.document.SigningIdentity;
import io.casehub.platform.api.signing.document.SigningProfile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DssDocumentSigningServiceTest {

    @TempDir
    static Path tempDir;
    static KeyStoreManager keyStoreManager;

    @BeforeAll
    static void setup() throws Exception {
        Path ks = TestKeyStoreHelper.createTestKeystore(tempDir);
        keyStoreManager = new KeyStoreManager(
                ks.toString(), TestKeyStoreHelper.PASSWORD,
                "PKCS12", TestKeyStoreHelper.ALIAS);
    }

    @Test
    void signPdf_bBProfile_producesSignedBytes() throws Exception {
        var service = new DssDocumentSigningService(keyStoreManager, SigningProfile.B_B, null);
        byte[] pdf = TestKeyStoreHelper.createMinimalPdf();
        var result = service.signPdf(pdf, new SigningIdentity("actor", "tenant"));
        assertThat(result).isPresent();
        assertThat(result.get().signedBytes().length).isGreaterThan(pdf.length);
        assertThat(result.get().signerDn()).contains("CN=Test Seal");
        assertThat(result.get().profile()).isEqualTo(SigningProfile.B_B);
        assertThat(result.get().signedAt()).isNotNull();
        assertThat(result.get().keyRef()).isNotNull();
    }

    @Test
    void signDetached_producesSignature() throws Exception {
        var service = new DssDocumentSigningService(keyStoreManager, SigningProfile.B_B, null);
        byte[] data = "test compliance report content".getBytes();
        var result = service.signDetached(data, new SigningIdentity("actor", "tenant"));
        assertThat(result).isPresent();
        assertThat(result.get().signatureBytes()).isNotEmpty();
        assertThat(result.get().signerDn()).contains("CN=Test Seal");
        assertThat(result.get().profile()).isEqualTo(SigningProfile.B_B);
    }

    @Test
    void signPdf_bTProfile_noTsa_throws() throws Exception {
        var service = new DssDocumentSigningService(keyStoreManager, SigningProfile.B_T, null);
        byte[] pdf = TestKeyStoreHelper.createMinimalPdf();
        assertThatThrownBy(() ->
                service.signPdf(pdf, new SigningIdentity("actor", "tenant")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TSA");
    }

    @Test
    void signDetached_bTProfile_noTsa_throws() {
        var service = new DssDocumentSigningService(keyStoreManager, SigningProfile.B_T, null);
        assertThatThrownBy(() ->
                service.signDetached("data".getBytes(), new SigningIdentity("actor", "tenant")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TSA");
    }

    @Test
    void signPdf_notLoaded_returnsEmpty() throws Exception {
        var emptyMgr = new KeyStoreManager(null, null, "PKCS12", null);
        var service = new DssDocumentSigningService(emptyMgr, SigningProfile.B_B, null);
        var result = service.signPdf(TestKeyStoreHelper.createMinimalPdf(),
                new SigningIdentity("a", "t"));
        assertThat(result).isEmpty();
    }

    @Test
    void signDetached_notLoaded_returnsEmpty() {
        var emptyMgr = new KeyStoreManager(null, null, "PKCS12", null);
        var service = new DssDocumentSigningService(emptyMgr, SigningProfile.B_B, null);
        var result = service.signDetached("data".getBytes(),
                new SigningIdentity("a", "t"));
        assertThat(result).isEmpty();
    }
}
