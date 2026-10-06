package io.casehub.platform.api.authn;

import java.util.Set;

public record SocialLoginCompleted(
    String provider, String actorId, String tenancyId,
    Set<String> grantedScopes, boolean firstLogin
) {}
