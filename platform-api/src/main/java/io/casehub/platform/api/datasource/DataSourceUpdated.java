package io.casehub.platform.api.datasource;

import java.util.Objects;

/**
 * CDI event fired by non-no-op {@link DataSourceRegistry} implementations after
 * every successful {@link DataSourceRegistry#update(DataSourceDescriptor)} call.
 *
 * <p>Carries the old and new descriptors plus the current DataSource instance.
 * The instance is necessary because marshalling changes may rebuild the decorator,
 * producing a new DataSource in the registry's sources map.
 */
public record DataSourceUpdated(
        DataSourceDescriptor oldDescriptor,
        DataSourceDescriptor newDescriptor,
        DataSource<?> dataSource) {

    public DataSourceUpdated {
        Objects.requireNonNull(oldDescriptor, "oldDescriptor");
        Objects.requireNonNull(newDescriptor, "newDescriptor");
        Objects.requireNonNull(dataSource, "dataSource");
    }
}
