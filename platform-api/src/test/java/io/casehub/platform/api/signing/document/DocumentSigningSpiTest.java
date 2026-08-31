package io.casehub.platform.api.signing.document;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DocumentSigningSpiTest {

    @Test
    void signingIdentity_carriesActorAndTenant() {
        var id = new SigningIdentity("system:compliance-signer", "tenant-acme");
        assertThat(id.actorId()).isEqualTo("system:compliance-signer");
        assertThat(id.tenancyId()).isEqualTo("tenant-acme");
    }

    @Test
    void signingProfile_allLevels() {
        assertThat(SigningProfile.values()).containsExactly(
                SigningProfile.B_B, SigningProfile.B_T,
                SigningProfile.B_LT, SigningProfile.B_LTA);
    }

    @Test
    void signingProfile_requiresTimestamp() {
        assertThat(SigningProfile.B_B.requiresTimestamp()).isFalse();
        assertThat(SigningProfile.B_T.requiresTimestamp()).isTrue();
        assertThat(SigningProfile.B_LT.requiresTimestamp()).isTrue();
        assertThat(SigningProfile.B_LTA.requiresTimestamp()).isTrue();
    }

    @Test
    void signedDocument_defensiveCopy() {
        byte[] original = {1, 2, 3};
        var doc = new SignedDocument(original, "CN=Test", Instant.now(),
                "keyRef123", SigningProfile.B_T);
        original[0] = 99;
        assertThat(doc.signedBytes()[0]).isEqualTo((byte) 1);
    }

    @Test
    void signedDocument_accessorDefensiveCopy() {
        byte[] original = {1, 2, 3};
        var doc = new SignedDocument(original, "CN=Test", Instant.now(),
                "keyRef123", SigningProfile.B_T);
        byte[] accessed = doc.signedBytes();
        accessed[0] = 99;
        assertThat(doc.signedBytes()[0]).isEqualTo((byte) 1);
    }

    @Test
    void detachedSignature_defensiveCopy() {
        byte[] original = {4, 5, 6};
        var sig = new DetachedSignature(original, "CN=Test", Instant.now(),
                "keyRef456", SigningProfile.B_T);
        original[0] = 99;
        assertThat(sig.signatureBytes()[0]).isEqualTo((byte) 4);
    }

    @Test
    void detachedSignature_accessorDefensiveCopy() {
        byte[] original = {4, 5, 6};
        var sig = new DetachedSignature(original, "CN=Test", Instant.now(),
                "keyRef456", SigningProfile.B_T);
        byte[] accessed = sig.signatureBytes();
        accessed[0] = 99;
        assertThat(sig.signatureBytes()[0]).isEqualTo((byte) 4);
    }

    @Test
    void verificationStatus_allValues() {
        assertThat(VerificationStatus.values()).containsExactly(
                VerificationStatus.VALID, VerificationStatus.INVALID,
                VerificationStatus.UNSIGNED, VerificationStatus.UNSUPPORTED_FORMAT,
                VerificationStatus.ERROR);
    }

    @Test
    void certificateInfo_fields() {
        var now = Instant.now();
        var later = now.plusSeconds(86400);
        var cert = new CertificateInfo("CN=Seal", "CN=CA", now, later, true);
        assertThat(cert.subjectDn()).isEqualTo("CN=Seal");
        assertThat(cert.issuerDn()).isEqualTo("CN=CA");
        assertThat(cert.validFrom()).isEqualTo(now);
        assertThat(cert.validTo()).isEqualTo(later);
        assertThat(cert.claimsQualified()).isTrue();
    }

    @Test
    void documentVerificationResult_unsigned() {
        var result = DocumentVerificationResult.unsigned();
        assertThat(result.status()).isEqualTo(VerificationStatus.UNSIGNED);
        assertThat(result.signerDn()).isNull();
        assertThat(result.signedAt()).isNull();
        assertThat(result.keyRef()).isNull();
        assertThat(result.detectedProfile()).isNull();
        assertThat(result.certificateChain()).isEmpty();
        assertThat(result.diagnosticMessage()).isNull();
    }
}
