package io.casehub.platform.api.authn;

public record AuthenticationSuccess(
    String actorId, String tenancyId, String method
) {}
