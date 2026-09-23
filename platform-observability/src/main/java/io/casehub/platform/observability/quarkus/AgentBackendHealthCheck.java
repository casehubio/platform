package io.casehub.platform.observability.quarkus;

import io.casehub.platform.agent.AgentBackend;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class AgentBackendHealthCheck implements HealthCheck {

    @Inject
    Instance<AgentBackend> backends;

    @Override
    public HealthCheckResponse call() {
        if (backends.isUnsatisfied()) {
            return HealthCheckResponse.up("agentBackends");
        }
        var keys = backends.stream().map(AgentBackend::key).toList();
        return HealthCheckResponse.named("agentBackends")
                .status(!keys.isEmpty())
                .withData("backendCount", keys.size())
                .build();
    }
}
