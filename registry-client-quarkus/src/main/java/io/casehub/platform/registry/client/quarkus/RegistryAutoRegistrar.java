package io.casehub.platform.registry.client.quarkus;

import io.casehub.platform.api.registry.HealthStatus;
import io.casehub.platform.api.registry.RegistryEntry;
import io.casehub.platform.registry.client.RegistryClient;
import io.casehub.platform.registry.client.RegistryClientException;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Startup
@Readiness
@ApplicationScoped
public class RegistryAutoRegistrar implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(RegistryAutoRegistrar.class);

    private final RegistryClientConfig config;
    private volatile RegistryClient client;
    private volatile String registeredId;
    private volatile boolean enabled;

    @Inject
    public RegistryAutoRegistrar(RegistryClientConfig config) {
        this.config = config;
    }

    @jakarta.annotation.PostConstruct
    void init() {
        if (config.url().isEmpty()) {
            LOG.info("Registry client disabled — casehub.registry.client.url not configured");
            return;
        }
        if (config.entryId().isEmpty()) {
            LOG.warn("Registry client disabled — casehub.registry.client.entry-id not configured");
            return;
        }
        if (config.tenancyId().isEmpty()) {
            LOG.warn("Registry client disabled — casehub.registry.client.tenancy-id not configured");
            return;
        }

        enabled = true;
        client = new RegistryClient(config.url().get());
        register();
    }

    private void register() {
        var id = config.entryId().get();
        var entry = new RegistryEntry(
                id,
                config.entryType(),
                config.namespace(),
                config.tenancyId().get(),
                Map.of(),
                Instant.now(),
                Instant.now(),
                Duration.ofSeconds(config.ttlSeconds()),
                HealthStatus.HEALTHY,
                null);

        try {
            client.register(entry);
            registeredId = id;
            LOG.infof("Registered with registry — id=%s", id);
        } catch (RegistryClientException e) {
            LOG.warnf(e, "Failed to register with registry — id=%s", id);
        }
    }

    @Scheduled(every = "${casehub.registry.client.heartbeat-interval:10s}")
    void heartbeat() {
        if (!enabled || registeredId == null) return;
        try {
            client.heartbeat(registeredId);
        } catch (RegistryClientException e) {
            LOG.warnf("Heartbeat failed for %s — attempting re-registration", registeredId);
            register();
        }
    }

    @PreDestroy
    void shutdown() {
        if (!enabled || registeredId == null) return;
        try {
            client.deregister(registeredId);
            LOG.infof("Deregistered from registry — id=%s", registeredId);
        } catch (RegistryClientException e) {
            LOG.warnf(e, "Failed to deregister — id=%s", registeredId);
        } finally {
            client.close();
        }
    }

    @Override
    public HealthCheckResponse call() {
        if (!enabled) {
            return HealthCheckResponse.named("registry-client")
                    .up()
                    .withData("status", "disabled")
                    .build();
        }
        return HealthCheckResponse.named("registry-client")
                .status(registeredId != null)
                .withData("entryId", config.entryId().orElse(""))
                .build();
    }
}
