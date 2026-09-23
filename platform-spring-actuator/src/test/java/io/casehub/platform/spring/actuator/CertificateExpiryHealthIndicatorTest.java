package io.casehub.platform.spring.actuator;

import io.casehub.platform.observability.CertificateExpiryResult;
import io.casehub.platform.observability.CertificateStatus;
import io.casehub.platform.observability.KeyStoreExpiryChecker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateExpiryHealthIndicatorTest {

    @Test
    void upWhenHealthy() {
        var checker = stubChecker(new CertificateExpiryResult(true, List.of(
                new CertificateStatus("test", "CN=test",
                        Instant.now().plus(365, ChronoUnit.DAYS), 365, false))));
        var indicator = new CertificateExpiryHealthIndicator(checker);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void downWhenExpired() {
        var checker = stubChecker(new CertificateExpiryResult(false, List.of(
                new CertificateStatus("test", "CN=test",
                        Instant.now().minus(1, ChronoUnit.DAYS), -1, true))));
        var indicator = new CertificateExpiryHealthIndicator(checker);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void warningWhenExpiringSoon() {
        var checker = stubChecker(new CertificateExpiryResult(false, List.of(
                new CertificateStatus("test", "CN=test",
                        Instant.now().plus(10, ChronoUnit.DAYS), 10, false))));
        var indicator = new CertificateExpiryHealthIndicator(checker);
        assertThat(indicator.health().getStatus().getCode()).isEqualTo("WARNING");
    }

    private KeyStoreExpiryChecker stubChecker(CertificateExpiryResult result) {
        return new KeyStoreExpiryChecker(null, null, "PKCS12", 30) {
            @Override
            public CertificateExpiryResult check() { return result; }
        };
    }
}
