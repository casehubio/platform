package io.casehub.platform.api.datasource;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped registry of DataSources.
 *
 * <p>Provides registration, resolution, discovery, and deregistration of DataSources
 * keyed by {@code (path, tenancyId)}.
 *
 * <p>{@code NoOpDataSourceRegistry @DefaultBean} is active when no backend module is on
 * the classpath. {@code InMemoryDataSourceRegistry @Alternative @Priority(100)} in
 * {@code casehub-platform-datasource-inmem} provides a working in-memory backend with
 * alpha network implementation.
 *
 * <h2>Tenant isolation</h2>
 * <p>All read operations filter by {@code tenancyId}. Platform-global DataSources
 * registered with {@link TenancyConstants#PLATFORM_TENANT_ID} are visible to all tenants.
 *
 * <h2>Write authorization</h2>
 * <p>The SPI enforces no write authorization — callers are accountable for ensuring
 * {@link DataSourceDescriptor#tenancyId()} matches their authority. The initial population
 * model ({@code @Startup @PostConstruct}) operates with implicit system authority.
 *
 * <h2>DataSourceRegistered CDI event</h2>
 * <p>Non-no-op implementations have a required obligation to fire
 * {@link DataSourceRegistered} via {@code Event<DataSourceRegistered>.fireAsync()} after
 * every successful {@link #register(DataSourceDescriptor)} call. The no-op
 * {@code @DefaultBean} implementation must NOT fire the event — it stores nothing,
 * and firing would trigger stream route creation for phantom DataSources.
 */
public interface DataSourceRegistry {

    /**
     * Register a DataSource and return its instance.
     *
     * <p>{@code (path, tenancyId)} is the unique key — upsert semantics: re-registering
     * the same key replaces the descriptor and returns a new DataSource instance.
     *
     * <p>The returned {@link DataSource} is backed by an alpha network. Calling
     * {@link DataSource#add(Object)} propagates to all active subscriptions.
     *
     * @return the DataSource instance for this descriptor
     */
    DataSource<?> register(DataSourceDescriptor descriptor);

    /**
     * Resolve a DataSource descriptor by path for the given tenant, applying priority lookup.
     *
     * <ol>
     *   <li>Returns the tenant-specific descriptor if one exists
     *       ({@code descriptor.tenancyId().equals(tenancyId)}).</li>
     *   <li>Otherwise returns the platform-global descriptor if one exists
     *       ({@code descriptor.tenancyId().equals(PLATFORM_TENANT_ID)}).</li>
     *   <li>Otherwise returns empty.</li>
     * </ol>
     *
     * <p>Tenant-specific takes precedence — allows tenants to override platform defaults.
     */
    Optional<DataSourceDescriptor> resolve(Path path, String tenancyId);

    /**
     * Resolve a DataSource instance by path for the given tenant, applying priority lookup.
     *
     * <p>Lookup semantics match {@link #resolve(Path, String)} — tenant-specific takes
     * precedence over platform-global.
     *
     * @return the DataSource instance if registered, otherwise empty
     */
    Optional<DataSource<?>> resolveSource(Path path, String tenancyId);

    /**
     * Discover DataSources matching the query criteria.
     *
     * <p>Always includes platform-global DataSources alongside the caller's tenant DataSources.
     * Returns both tenant-specific and platform-global matches — no override semantics;
     * use {@link #resolve(Path, String)} when a single authoritative result is required.
     *
     * <p>Complete predicate — see {@link DataSourceQuery} for the full two-condition
     * conjunction that every implementation must enforce.
     *
     * <p>The result list is unordered. Implementations must not guarantee a specific
     * ordering, and callers must not depend on one.
     */
    List<DataSourceDescriptor> discover(DataSourceQuery query);

    /**
     * Deregister by {@code (path, tenancyId)}. No-op if not found.
     *
     * <p>Deregistering stops further deliveries to all active subscriptions on that
     * DataSource, but does not invalidate existing {@link SubscriptionHandle} instances —
     * {@link SubscriptionHandle#isActive()} becomes {@code false}.
     */
    void deregister(Path path, String tenancyId);
}
