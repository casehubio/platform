package io.casehub.platform.signing.lifecycle;

import io.casehub.platform.api.subscription.SubscribableEvent;
import java.time.Instant;

public record CertificateExpiryEvent(
        String alias,
        String subjectDn,
        Instant notAfter,
        long daysUntilExpiry,
        boolean expired) implements SubscribableEvent {

    public static final String EVENT_TYPE = "certificate.expiry";

    @Override
    public String type() {
        return EVENT_TYPE;
    }

    @Override
    public String tenancyId() {
        return null;
    }
}
