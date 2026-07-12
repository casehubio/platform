package io.casehub.platform.api.datasource;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable description of a registered DataSource in the {@link DataSourceRegistry}.
 *
 * <p>The unique key is {@code (path, tenancyId)}. Registration is idempotent —
 * re-registering the same key returns the existing DataSource (first descriptor wins).
 *
 * <p>Field order: key components ({@code path}, {@code tenancyId}) lead, followed by
 * type metadata ({@code objectType}), then optional integration fields
 * ({@code endpointPath}, {@code acceptedEventTypes}, {@code properties}, {@code marshallerKeys}).
 *
 * <p>{@code endpointPath} is nullable — {@code null} indicates a case-scoped DataSource
 * (created per-case by the engine). Non-null values point to an endpoint in the
 * {@link io.casehub.platform.api.endpoints.EndpointRegistry}.
 *
 * <p>{@code acceptedEventTypes} declares which CloudEvent {@code type} values this
 * DataSource accepts when driven by a stream endpoint. Empty means "accept all types."
 *
 * <p>{@code properties} holds non-secret configuration. No reserved keys yet — future
 * use for DataSource-specific config.
 *
 * <p>{@code marshallerKeys} maps accepted event types to marshaller keys in the
 * {@link MarshallerRegistry}. Empty map means no marshalling. Unmapped event types
 * pass through without marshalling.
 */
public record DataSourceDescriptor(
        Path path,
        String tenancyId,
        ObjectType<?> objectType,
        Path endpointPath,
        Set<String> acceptedEventTypes,
        Map<String, String> properties,
        Map<String, String> marshallerKeys
) {

    public DataSourceDescriptor {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(objectType, "objectType");
        Objects.requireNonNull(acceptedEventTypes, "acceptedEventTypes");
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(marshallerKeys, "marshallerKeys");
        acceptedEventTypes = Set.copyOf(acceptedEventTypes);
        properties         = Map.copyOf(properties);
        marshallerKeys     = Map.copyOf(marshallerKeys);
    }

    /**
     * Returns {@code true} if this DataSource is platform-global (visible to all tenants).
     * Platform-global DataSources are registered with
     * {@link TenancyConstants#PLATFORM_TENANT_ID}.
     */
    public boolean isPlatformGlobal() {
        return TenancyConstants.PLATFORM_TENANT_ID.equals(tenancyId);
    }
}
