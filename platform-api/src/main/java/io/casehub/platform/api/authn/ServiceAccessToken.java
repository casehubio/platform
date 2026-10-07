package io.casehub.platform.api.authn;

import java.time.Instant;
import java.util.Set;

public record ServiceAccessToken(
    String accessToken,
    Instant expiresAt,
    Set<String> grantedScopes
) {}
