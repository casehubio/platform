package io.casehub.platform.authn.social;

import java.util.Objects;

public record OAuthIdentity(
    String externalId,
    String email,
    String displayName,
    boolean emailVerified
) {
    public OAuthIdentity {
        Objects.requireNonNull(externalId, "externalId");
    }

    public OAuthIdentity(String externalId, String email, String displayName) {
        this(externalId, email, displayName, false);
    }
}
