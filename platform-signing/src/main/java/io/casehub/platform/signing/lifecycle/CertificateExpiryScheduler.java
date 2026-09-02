package io.casehub.platform.signing.lifecycle;

import io.casehub.platform.signing.document.DssSigningConfig;
import io.casehub.platform.signing.document.KeyStoreManager;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.time.Clock;

@ApplicationScoped
public class CertificateExpiryScheduler {

    @Inject KeyStoreManager keyStoreManager;
    @Inject Event<CertificateExpiryEvent> expiryEvent;
    @Inject DssSigningConfig config;

    @Scheduled(every = "6h", identity = "cert-expiry-check")
    void check() {
        int warningDays = config.expiryWarningDays().orElse(30);
        var monitor = new CertificateExpiryMonitor(
                keyStoreManager, warningDays, Clock.systemUTC(), expiryEvent::fireAsync);
        monitor.check();
    }
}
