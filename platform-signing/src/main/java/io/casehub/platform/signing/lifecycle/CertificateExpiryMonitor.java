package io.casehub.platform.signing.lifecycle;

import io.casehub.platform.signing.document.KeyStoreManager;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

public class CertificateExpiryMonitor {

    private static final Logger LOG = Logger.getLogger(CertificateExpiryMonitor.class);

    private final KeyStoreManager keyStoreManager;
    private final int warningDays;
    private final Clock clock;
    private final Consumer<CertificateExpiryEvent> eventSink;

    public CertificateExpiryMonitor(KeyStoreManager keyStoreManager, int warningDays,
                                     Clock clock, Consumer<CertificateExpiryEvent> eventSink) {
        this.keyStoreManager = keyStoreManager;
        this.warningDays = warningDays;
        this.clock = clock;
        this.eventSink = eventSink;
    }

    public void check() {
        if (!keyStoreManager.isLoaded()) return;

        Instant now = clock.instant();
        Instant threshold = now.plus(Duration.ofDays(warningDays));

        for (var key : keyStoreManager.getKeys()) {
            var cert = key.getCertificate();
            Instant notAfter = cert.getNotAfter().toInstant();
            String subjectDn = cert.getSubject().getPrincipal().getName();
            String alias = subjectDn;

            if (notAfter.isBefore(threshold)) {
                long daysUntilExpiry = Duration.between(now, notAfter).toDays();
                boolean expired = notAfter.isBefore(now);

                if (expired) {
                    LOG.errorf("Certificate EXPIRED: %s (expired %d days ago)", subjectDn, -daysUntilExpiry);
                } else {
                    LOG.warnf("Certificate expiring soon: %s (%d days remaining)", subjectDn, daysUntilExpiry);
                }

                eventSink.accept(new CertificateExpiryEvent(
                        alias, subjectDn, notAfter, daysUntilExpiry, expired));
            }
        }
    }
}
