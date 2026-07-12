package io.casehub.platform.api.datasource;

import java.util.Objects;

/**
 * CDI event fired by non-no-op {@link DataSourceRegistry} implementations after every
 * successful {@link DataSourceRegistry#deregister} call.
 *
 * <p>Consumers use this event to react to DataSource removals at runtime (e.g., router
 * unwiring routes, subscription engine releasing handles).
 *
 * <p>Carries both the descriptor and the {@link DataSource} instance being deregistered.
 * The instance enables identity-based comparison in CDI observers — necessary because
 * {@code @ObservesAsync} does not guarantee event ordering during deregister + register
 * sequences.
 *
 * <p>The no-op {@code @DefaultBean} implementation must NOT fire this event — it stores
 * nothing, and firing would trigger cleanup for phantom DataSources.
 *
 * <p>Any future non-no-op {@link DataSourceRegistry} implementation has a required
 * obligation to fire this event after marking the DataSource for removal.
 */
public record DataSourceDeregistered(DataSourceDescriptor descriptor, DataSource<?> dataSource) {

    public DataSourceDeregistered {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(dataSource, "dataSource");
    }
}
