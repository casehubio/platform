package io.casehub.platform.api.authn;

public class UnknownMethodException extends AuthenticationException {
    public UnknownMethodException(String method) { super(method, "No provider registered for method: " + method); }
}
