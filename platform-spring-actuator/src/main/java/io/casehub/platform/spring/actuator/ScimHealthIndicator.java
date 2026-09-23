package io.casehub.platform.spring.actuator;

import io.casehub.platform.scim.ScimClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

class ScimHealthIndicator implements HealthIndicator {

    private final ScimClient client;

    ScimHealthIndicator(ScimClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            client.listGroups("id pr", "id");
            return Health.up().build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
