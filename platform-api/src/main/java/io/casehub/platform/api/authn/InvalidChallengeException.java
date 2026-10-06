package io.casehub.platform.api.authn;

public class InvalidChallengeException extends AuthenticationException {
    public InvalidChallengeException(String method, String message) { super(method, message); }
}
