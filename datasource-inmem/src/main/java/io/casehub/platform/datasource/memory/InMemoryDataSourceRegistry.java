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
 * <h2>DataSourceRegistered CDI event</h2>
 * <p>Fires {@link DataSourceRegistered} via {@code fireAsync()} after every successful
 * {@link #register(DataSourceDescriptor)} call. Observer exceptions are WARN-logged;
 * the registry operation itself has already succeeded before the event fires.
 * The CDI-proxy path (and unit tests) use a package-private no-arg constructor
 * that leaves {@code dataSourceRegisteredEvent} null; the null guard in
 * {@code register()} prevents NPE in those paths.
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

    @Inject
    public InMemoryDataSourceRegistry(Event<DataSourceRegistered> dataSourceRegisteredEvent) {
        this.dataSourceRegisteredEvent = dataSourceRegisteredEvent;
    }

    /** Used by CDI proxy subclass (synthetic bytecode) and plain JUnit5 unit tests (same package). */
    InMemoryDataSourceRegistry() {
        this.dataSourceRegisteredEvent = null;
    }

    @Override
    public DataSource<?> register(final DataSourceDescriptor descriptor) {
        final RegistryKey key = new RegistryKey(descriptor.path().value(), descriptor.tenancyId());
        final DataSource<?> dataSource = new AlphaDataSource<>();
        descriptors.put(key, descriptor);
        sources.put(key, dataSource);

        if (dataSourceRegisteredEvent != null) {
            dataSourceRegisteredEvent.fireAsync(new DataSourceRegistered(descriptor))
                .whenComplete((e, t) -> {
                    if (t != null) {
                        LOG.warnf(t, "DataSourceRegistered observer failed for path %s",
                            descriptor.path());
                    }
                });
        }

        return dataSource;
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
        descriptors.remove(key);
        sources.remove(key);
    }

    private static boolean matchesTenancy(final DataSourceDescriptor d, final String tenancyId) {
        return d.tenancyId().equals(tenancyId)
                || d.tenancyId().equals(TenancyConstants.PLATFORM_TENANT_ID);
    }
}
