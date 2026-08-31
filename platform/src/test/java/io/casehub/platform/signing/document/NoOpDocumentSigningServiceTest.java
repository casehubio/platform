package io.casehub.platform.signing.document;

import io.casehub.platform.api.signing.document.SigningIdentity;
import io.casehub.platform.api.signing.document.VerificationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class NoOpDocumentSigningServiceTest {

    private final NoOpDocumentSigningService signing = new NoOpDocumentSigningService();
    private final NoOpDocumentVerificationService verification = new NoOpDocumentVerificationService();

    @Test
    void signPdf_returnsEmpty() {
        var result = signing.signPdf(new byte[]{1}, new SigningIdentity("a", "t"));
        assertThat(result).isEmpty();
    }

    @Test
    void signDetached_returnsEmpty() {
        var result = signing.signDetached(new byte[]{1}, new SigningIdentity("a", "t"));
        assertThat(result).isEmpty();
    }

    @Test
    void verifyPdf_returnsUnsigned() {
        var result = verification.verifyPdf(new byte[]{1});
        assertThat(result.status()).isEqualTo(VerificationStatus.UNSIGNED);
    }

    @Test
    void verifyDetached_returnsUnsigned() {
        var result = verification.verifyDetached(new byte[]{1}, new byte[]{2});
        assertThat(result.status()).isEqualTo(VerificationStatus.UNSIGNED);
    }
}
