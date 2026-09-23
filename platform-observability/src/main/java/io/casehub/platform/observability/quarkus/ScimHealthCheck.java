package io.casehub.platform.observability.quarkus;

import io.casehub.platform.scim.ScimClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class ScimHealthCheck implements HealthCheck {

    @Inject
    Instance<ScimClient> scimClient;

    @Override
    public HealthCheckResponse call() {
        if (scimClient.isUnsatisfied()) {
            return HealthCheckResponse.up("scim");
        }
        try {
            scimClient.get().listGroups("id pr", "id");
            return HealthCheckResponse.up("scim");
        } catch (Exception e) {
            return HealthCheckResponse.named("scim")
                    .down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
