package io.casehub.platform.observability;

import java.util.List;

public record CertificateExpiryResult(
        boolean healthy,
        List<CertificateStatus> certificates) {

    public static CertificateExpiryResult empty() {
        return new CertificateExpiryResult(true, List.of());
    }
}
