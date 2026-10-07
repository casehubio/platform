package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationException;

public class OAuthException extends AuthenticationException {
    public OAuthException(String message) {
        super("oauth", message);
    }

    public OAuthException(String message, Throwable cause) {
        super("oauth", message, cause);
    }
}
