package io.casehub.platform.api.authn;

import java.time.Instant;
import java.util.Set;

public record ServiceConnection(
    String actorId,
    String provider,
    String tenancyId,
    ServiceConnectionStatus status,
    Set<String> grantedScopes,
    Set<String> missingScopes,
    Instant connectedAt
) {}
