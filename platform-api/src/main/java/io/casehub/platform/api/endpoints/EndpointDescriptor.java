package io.casehub.platform.api.endpoints;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable description of a named endpoint in the {@link EndpointRegistry}.
 *
 * <p>The unique key is {@code (path, tenancyId)}. Re-registering the same key replaces
 * the descriptor — there is no merge semantics. A single descriptor per key declares the
 * complete capability set.
 *
 * <p>Field order: key components ({@code path}, {@code tenancyId}) lead, followed by
 * descriptor fields ({@code type}, {@code protocol}), then payload ({@code properties},
 * {@code credentialRef}, {@code capabilities}).
 *
 * <p>{@code properties} holds non-secret configuration (URLs, topic names, ports).
 * See {@link EndpointPropertyKeys} for reserved cross-module keys. Credentials are
 * never stored inline — use {@code credentialRef} to name the credential in the
 * secrets backend.
 */
public record EndpointDescriptor(
        Path path,
        String tenancyId,
        EndpointType type,
        EndpointProtocol protocol,
        Map<String, String> properties,
        String credentialRef,
        Set<EndpointCapability> capabilities
) {

    public EndpointDescriptor {
        Objects.requireNonNull(path,         "path");
        Objects.requireNonNull(tenancyId,    "tenancyId");
        Objects.requireNonNull(type,         "type");
        Objects.requireNonNull(protocol,     "protocol");
        Objects.requireNonNull(properties,   "properties");
        Objects.requireNonNull(capabilities, "capabilities");
        properties   = Map.copyOf(properties);
        capabilities = Set.copyOf(capabilities);
    }

    /**
     * Returns {@code true} if this endpoint is platform-global (visible to all tenants).
     * Platform-global endpoints are registered with
     * {@link TenancyConstants#PLATFORM_TENANT_ID}.
     */
    public boolean isPlatformGlobal() {
        return TenancyConstants.PLATFORM_TENANT_ID.equals(tenancyId);
    }
}
