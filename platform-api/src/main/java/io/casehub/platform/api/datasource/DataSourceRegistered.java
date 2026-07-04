package io.casehub.platform.api.datasource;

import java.util.Objects;

/**
 * CDI event fired by non-no-op {@link DataSourceRegistry} implementations after every
 * successful {@link DataSourceRegistry#register(DataSourceDescriptor)} call.
 *
 * <p>Consumers use this event to react to new DataSource registrations at runtime
 * (e.g., stream modules building routes, engine creating case-scoped subscriptions).
 * The no-op {@code @DefaultBean} implementation must NOT fire this event — firing it
 * would trigger route creation for phantom DataSources that are never actually stored.
 *
 * <p>Any future non-no-op {@link DataSourceRegistry} implementation has a required
 * obligation to fire this event after storing the descriptor and creating the DataSource.
 */
public record DataSourceRegistered(DataSourceDescriptor descriptor) {

    public DataSourceRegistered {
        Objects.requireNonNull(descriptor, "descriptor");
    }
}
