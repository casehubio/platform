package io.casehub.platform.signing.lifecycle;

import java.time.Instant;

public record CertificateExpiryEvent(
        String alias,
        String subjectDn,
        Instant notAfter,
        long daysUntilExpiry,
        boolean expired) {}
