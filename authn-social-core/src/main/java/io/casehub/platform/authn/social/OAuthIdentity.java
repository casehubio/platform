package io.casehub.platform.authn.social;

import java.util.Objects;

public record OAuthIdentity(
    String externalId,
    String email,
    String displayName
) {
    public OAuthIdentity {
        Objects.requireNonNull(externalId, "externalId");
    }
}
