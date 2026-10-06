package io.casehub.platform.api.authn;

import io.casehub.platform.api.identity.PrincipalId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record AuthenticationContext(
    String method,
    String tenancyId,
    String origin,
    Optional<PrincipalId> existingPrincipal,
    Map<String, Object> hints
) {
    public AuthenticationContext {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(origin, "origin");
        existingPrincipal = existingPrincipal != null ? existingPrincipal : Optional.empty();
        hints = hints != null ? Map.copyOf(hints) : Map.of();
    }
}
