package io.casehub.platform.datasource.memory;

import io.casehub.platform.api.datasource.*;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Volatile in-memory {@link DataSourceRegistry} with alpha network.
 *
 * <p>{@code @Alternative @Priority(100)} — Tier 4 in the CDI priority ladder.
 * Beats the {@code @DefaultBean} no-op implementation when on the classpath.
 *
 * <p>Thread-safe. Data is ephemeral (lost on restart). Suitable for tests and
 * zero-config ephemeral single-node installs. Do NOT combine with a persistent
 * DataSource backend in the same deployment scope.
 *
 * <p>{@link DataSourceRegistry#discover(DataSourceQuery)} iteration is weakly consistent —
 * concurrent modifications (register/deregister) may or may not be visible to an
 * in-flight discover call. This is acceptable for the in-memory use case.
 *
 * <h2>Registration semantics</h2>
 * <p>Idempotent — {@link #register(DataSourceDescriptor)} returns the existing
 * {@link DataSource} if the key is already registered and active. Only creates a new
 * instance for genuinely new registrations or re-registration of a draining DataSource.
 * Fires {@link DataSourceRegistered} via {@code fireAsync()} only for new instances.
 *
 * <h2>Deregistration semantics</h2>
 * <p>{@link #deregister(Path, String)} marks the DataSource for removal and fires
 * {@link DataSourceDeregistered}. Map cleanup is deferred until the share count (active
 * subscriber count) reaches zero. The cleanup callback uses identity-based conditional
 * removal to prevent corruption when a replacement DataSource exists.
 */
@Alternative
@Priority(100)
@ApplicationScoped
public class InMemoryDataSourceRegistry implements DataSourceRegistry {

    private static final Logger LOG = Logger.getLogger(InMemoryDataSourceRegistry.class);

    private final ConcurrentHashMap<RegistryKey, DataSourceDescriptor> descriptors =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RegistryKey, DataSource<?>> sources =
            new ConcurrentHashMap<>();

    private final Event<DataSourceRegistered> dataSourceRegisteredEvent;
    private final Event<DataSourceDeregistered> dataSourceDeregisteredEvent;

    @Inject
    public InMemoryDataSourceRegistry(Event<DataSourceRegistered> dataSourceRegisteredEvent,
                                       Event<DataSourceDeregistered> dataSourceDeregisteredEvent) {
        this.dataSourceRegisteredEvent = dataSourceRegisteredEvent;
        this.dataSourceDeregisteredEvent = dataSourceDeregisteredEvent;
    }

    InMemoryDataSourceRegistry() {
        this.dataSourceRegisteredEvent = null;
        this.dataSourceDeregisteredEvent = null;
    }

    @Override
    public DataSource<?> register(final DataSourceDescriptor descriptor) {
        final RegistryKey key = new RegistryKey(descriptor.path().value(), descriptor.tenancyId());
        final boolean[] created = {false};

        DataSource<?> result = sources.compute(key, (k, existing) -> {
            if (existing instanceof AlphaDataSource<?> alpha && !alpha.isPendingRemoval()) {
                return existing;
            }
            created[0] = true;
            return new AlphaDataSource<>();
        });

        if (created[0]) {
            descriptors.put(key, descriptor);
            if (dataSourceRegisteredEvent != null) {
                dataSourceRegisteredEvent.fireAsync(new DataSourceRegistered(descriptor))
                    .whenComplete((e, t) -> {
                        if (t != null) {
                            LOG.warnf(t, "DataSourceRegistered observer failed for path %s",
                                descriptor.path());
                        }
                    });
            }
        }

        return result;
    }

    @Override
    public Optional<DataSourceDescriptor> resolve(final Path path, final String tenancyId) {
        final DataSourceDescriptor tenant = descriptors.get(
                new RegistryKey(path.value(), tenancyId));
        if (tenant != null) return Optional.of(tenant);
        final DataSourceDescriptor global = descriptors.get(
                new RegistryKey(path.value(), TenancyConstants.PLATFORM_TENANT_ID));
        return Optional.ofNullable(global);
    }

    @Override
    public Optional<DataSource<?>> resolveSource(final Path path, final String tenancyId) {
        final DataSource<?> tenant = sources.get(
                new RegistryKey(path.value(), tenancyId));
        if (tenant != null) return Optional.of(tenant);
        final DataSource<?> global = sources.get(
                new RegistryKey(path.value(), TenancyConstants.PLATFORM_TENANT_ID));
        return Optional.ofNullable(global);
    }

    @Override
    public List<DataSourceDescriptor> discover(final DataSourceQuery query) {
        return descriptors.values().stream()
                .filter(d -> matchesTenancy(d, query.tenancyId()))
                .filter(d -> query.objectType() == null
                        || d.objectType().getTypeKey().equals(query.objectType().getTypeKey()))
                .toList();
    }

    @Override
    public void deregister(final Path path, final String tenancyId) {
        final RegistryKey key = new RegistryKey(path.value(), tenancyId);
        final AlphaDataSource<?> source = (AlphaDataSource<?>) sources.get(key);
        if (source == null) {
            return;
        }
        final DataSourceDescriptor descriptor = descriptors.get(key);

        source.markForRemoval(() -> {
            if (sources.remove(key, source)) {
                descriptors.remove(key);
            }
        });

        if (descriptor != null && dataSourceDeregisteredEvent != null) {
            dataSourceDeregisteredEvent.fireAsync(new DataSourceDeregistered(descriptor, source))
                .whenComplete((e, t) -> {
                    if (t != null) {
                        LOG.warnf(t, "DataSourceDeregistered observer failed for path %s", path);
                    }
                });
        }
    }

    private static boolean matchesTenancy(final DataSourceDescriptor d, final String tenancyId) {
        return d.tenancyId().equals(tenancyId)
                || d.tenancyId().equals(TenancyConstants.PLATFORM_TENANT_ID);
    }
}
