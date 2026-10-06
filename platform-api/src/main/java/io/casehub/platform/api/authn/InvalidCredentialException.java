package io.casehub.platform.api.authn;

public class InvalidCredentialException extends AuthenticationException {
    public InvalidCredentialException(String method, String message) { super(method, message); }
}
