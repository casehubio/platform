package io.casehub.platform.api.authn;

import java.util.Objects;
import java.util.Set;

public record ConsentRequest(
    String authorizationUrl,
    String provider,
    Set<String> requestedScopes,
    String state
) {
    public ConsentRequest {
        Objects.requireNonNull(authorizationUrl, "authorizationUrl");
        Objects.requireNonNull(provider, "provider");
        requestedScopes = Set.copyOf(requestedScopes);
        Objects.requireNonNull(state, "state");
    }
}
