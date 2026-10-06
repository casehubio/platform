package io.casehub.platform.api.authn;

import java.time.Instant;
import java.util.Objects;

public record IdentityBinding(
    String provider,
    String externalId,
    String actorId,
    String tenancyId,
    String email,
    Instant createdAt
) {
    public IdentityBinding {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(tenancyId, "tenancyId");
    }
}
