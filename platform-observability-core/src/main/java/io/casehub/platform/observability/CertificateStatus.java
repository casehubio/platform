package io.casehub.platform.observability;

import java.time.Instant;

public record CertificateStatus(
        String alias,
        String subjectDn,
        Instant notAfter,
        long daysRemaining,
        boolean expired) {}
