package io.casehub.platform.api.datasource;

import java.util.Objects;

/**
 * Criteria for {@link DataSourceRegistry#discover(DataSourceQuery)}.
 *
 * <p>Field order: required field ({@code tenancyId}) leads, nullable filter field follows.
 *
 * <p>A descriptor matches when both conditions hold:
 * <pre>{@code
 * (descriptor.tenancyId == tenancyId  OR  descriptor.tenancyId == PLATFORM_TENANT_ID)
 * AND (objectType == null  OR  descriptor.objectType.getTypeKey().equals(objectType.getTypeKey()))
 * }</pre>
 *
 * <p>{@code objectType} being {@code null} acts as a wildcard — matches all types.
 */
public record DataSourceQuery(
        String tenancyId,
        ObjectType<?> objectType
) {

    public DataSourceQuery {
        Objects.requireNonNull(tenancyId, "tenancyId");
    }
}
