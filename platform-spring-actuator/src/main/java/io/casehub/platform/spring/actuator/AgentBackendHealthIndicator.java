package io.casehub.platform.spring.actuator;

import io.casehub.platform.agent.AgentBackend;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.List;

class AgentBackendHealthIndicator implements HealthIndicator {

    private final List<AgentBackend> backends;

    AgentBackendHealthIndicator(List<AgentBackend> backends) {
        this.backends = backends;
    }

    @Override
    public Health health() {
        if (backends.isEmpty()) {
            return Health.down().withDetail("backends", List.of()).build();
        }
        var keys = backends.stream().map(AgentBackend::key).toList();
        return Health.up()
                .withDetail("backendCount", backends.size())
                .withDetail("backends", keys)
                .build();
    }
}
