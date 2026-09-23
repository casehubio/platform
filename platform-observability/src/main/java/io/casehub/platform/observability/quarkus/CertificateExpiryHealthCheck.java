package io.casehub.platform.observability.quarkus;

import io.casehub.platform.observability.KeyStoreExpiryChecker;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class CertificateExpiryHealthCheck implements HealthCheck {

    @Inject
    Instance<KeyStoreExpiryChecker> checker;

    @Override
    public HealthCheckResponse call() {
        if (checker.isUnsatisfied()) {
            return HealthCheckResponse.up("certificateExpiry");
        }
        var result = checker.get().check();
        if (result.error() != null) {
            return HealthCheckResponse.named("certificateExpiry")
                    .down()
                    .withData("error", result.error())
                    .build();
        }
        if (result.certificates().isEmpty()) {
            return HealthCheckResponse.named("certificateExpiry").up().build();
        }
        var builder = HealthCheckResponse.named("certificateExpiry")
                .status(result.healthy());
        for (var cert : result.certificates()) {
            builder.withData(cert.alias() + ".daysRemaining", cert.daysRemaining());
            builder.withData(cert.alias() + ".expired", cert.expired());
        }
        return builder.build();
    }
}
