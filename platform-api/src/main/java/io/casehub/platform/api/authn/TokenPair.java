package io.casehub.platform.api.authn;

public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {}
