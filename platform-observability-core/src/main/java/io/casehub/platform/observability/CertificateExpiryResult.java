package io.casehub.platform.observability;

import java.util.List;

public record CertificateExpiryResult(
        boolean healthy,
        List<CertificateStatus> certificates,
        String error) {

    public CertificateExpiryResult(boolean healthy, List<CertificateStatus> certificates) {
        this(healthy, certificates, null);
    }

    public static CertificateExpiryResult empty() {
        return new CertificateExpiryResult(true, List.of(), null);
    }

    public static CertificateExpiryResult error(String message) {
        return new CertificateExpiryResult(false, List.of(), message);
    }
}
