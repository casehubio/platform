package io.casehub.platform.api.authn;

public record AuthenticationFailure(
    String method, String reason
) {}
