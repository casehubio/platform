package io.casehub.platform.api.authn;

import io.casehub.platform.api.identity.PrincipalId;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record AuthenticationResult(
    PrincipalId principal,
    String tenancyId,
    Set<String> groups,
    String method,
    Map<String, Object> metadata
) {
    public AuthenticationResult {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(method, "method");
        groups = groups != null ? Set.copyOf(groups) : Set.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}
