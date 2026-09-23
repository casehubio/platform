package io.casehub.platform.observability;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KeyStoreExpiryChecker {

    private final String path;
    private final char[] password;
    private final String type;
    private final int warningDays;

    public KeyStoreExpiryChecker(String path, char[] password, String type, int warningDays) {
        this.path = path;
        this.password = password;
        this.type = type;
        this.warningDays = warningDays;
    }

    public CertificateExpiryResult check() {
        if (path == null || path.isBlank()) {
            return CertificateExpiryResult.empty();
        }

        try {
            var ks = KeyStore.getInstance(type);
            try (var fis = new FileInputStream(path)) {
                ks.load(fis, password);
            }

            var now = Instant.now();
            var threshold = now.plus(Duration.ofDays(warningDays));
            var statuses = new ArrayList<CertificateStatus>();
            boolean healthy = true;

            for (var alias : Collections.list(ks.aliases())) {
                var cert = ks.getCertificate(alias);
                if (cert instanceof X509Certificate x509) {
                    var notAfter = x509.getNotAfter().toInstant();
                    long daysRemaining = Duration.between(now, notAfter).toDays();
                    boolean expired = notAfter.isBefore(now);
                    statuses.add(new CertificateStatus(
                            alias, x509.getSubjectX500Principal().getName(),
                            notAfter, daysRemaining, expired));
                    if (expired || notAfter.isBefore(threshold)) {
                        healthy = false;
                    }
                }
            }
            return new CertificateExpiryResult(healthy, List.copyOf(statuses));
        } catch (Exception e) {
            return new CertificateExpiryResult(false, List.of());
        }
    }
}
