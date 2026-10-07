package io.casehub.platform.api.authn;

public abstract class AuthenticationException extends RuntimeException {
    private final String method;

    protected AuthenticationException(String method, String message) {
        super(message);
        this.method = method;
    }

    protected AuthenticationException(String method, String message, Throwable cause) {
        super(message, cause);
        this.method = method;
    }


    public String method() { return method; }
}
