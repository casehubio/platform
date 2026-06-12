package io.casehub.platform.api.endpoints;

import java.util.Objects;
import java.util.Set;

/**
 * Criteria for {@link EndpointRegistry#discover(EndpointQuery)}.
 *
 * <p>Field order: required field ({@code tenancyId}) leads, nullable filter fields follow.
 *
 * <p>A descriptor matches when all four conditions hold:
 * <pre>{@code
 * (descriptor.tenancyId == tenancyId  OR  descriptor.tenancyId == PLATFORM_TENANT_ID)
 * AND (type     == null  OR  descriptor.type     == type)
 * AND (protocol == null  OR  descriptor.protocol == protocol)
 * AND  descriptor.capabilities.containsAll(requiredCapabilities)
 * }</pre>
 *
 * <p>{@code requiredCapabilities} being empty matches all descriptors (every set
 * {@code containsAll} the empty set). {@code type} or {@code protocol} being {@code null}
 * acts as a wildcard for that field.
 */
public record EndpointQuery(
        String tenancyId,
        EndpointType type,
        EndpointProtocol protocol,
        Set<EndpointCapability> requiredCapabilities
) {

    public EndpointQuery {
        Objects.requireNonNull(tenancyId,            "tenancyId");
        Objects.requireNonNull(requiredCapabilities, "requiredCapabilities");
        requiredCapabilities = Set.copyOf(requiredCapabilities);
    }
}
