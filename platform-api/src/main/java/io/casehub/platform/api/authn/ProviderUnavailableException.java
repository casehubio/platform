package io.casehub.platform.api.authn;

public class ProviderUnavailableException extends AuthenticationException {
    public ProviderUnavailableException(String method, String message) { super(method, message); }
}
