package io.casehub.platform.spring.actuator;

import io.casehub.platform.observability.CertificateStatus;
import io.casehub.platform.observability.KeyStoreExpiryChecker;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.Map;

class CertificateExpiryHealthIndicator implements HealthIndicator {

    private final KeyStoreExpiryChecker checker;

    CertificateExpiryHealthIndicator(KeyStoreExpiryChecker checker) {
        this.checker = checker;
    }

    @Override
    public Health health() {
        var result = checker.check();
        if (result.certificates().isEmpty()) {
            return Health.unknown().withDetail("reason", "no certificates found").build();
        }
        var certDetails = result.certificates().stream()
                .map(c -> Map.<String, Object>of(
                        "alias", c.alias(),
                        "subjectDn", c.subjectDn(),
                        "daysRemaining", c.daysRemaining(),
                        "expired", c.expired()))
                .toList();
        if (result.healthy()) {
            return Health.up().withDetail("certificates", certDetails).build();
        }
        boolean anyExpired = result.certificates().stream().anyMatch(CertificateStatus::expired);
        var builder = anyExpired ? Health.down() : Health.status("WARNING");
        return builder.withDetail("certificates", certDetails).build();
    }
}
