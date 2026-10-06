package io.casehub.platform.authn.social;

import java.util.Objects;

public record OAuthTokenResponse(
    String accessToken,
    String refreshToken,
    String idToken,
    long expiresIn,
    String tokenType,
    String scope
) {
    public OAuthTokenResponse {
        Objects.requireNonNull(accessToken, "accessToken");
    }
}
