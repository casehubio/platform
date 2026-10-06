package io.casehub.platform.api.authn;

public record OAuthTokenRefreshFailed(
    String actorId, String tenancyId, String provider, String reason
) {}
