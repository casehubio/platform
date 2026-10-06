package io.casehub.platform.api.authn;

public class AccountLockedException extends AuthenticationException {
    public AccountLockedException(String method, String message) { super(method, message); }
}
