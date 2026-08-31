package io.casehub.platform.api.signing.document;

import java.time.Instant;

public record CertificateInfo(String subjectDn, String issuerDn,
                               Instant validFrom, Instant validTo,
                               boolean claimsQualified) {}
