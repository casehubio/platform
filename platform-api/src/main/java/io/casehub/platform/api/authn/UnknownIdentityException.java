package io.casehub.platform.api.authn;

public class UnknownIdentityException extends AuthenticationException {
    public UnknownIdentityException(String method, String message) { super(method, message); }
}
