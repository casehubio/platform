package io.casehub.platform.api.authn;

public class AccountNotActiveException extends AuthenticationException {
    private final String statusName;

    public AccountNotActiveException(String method, String statusName) {
        super(method, "Account is " + statusName.toLowerCase());
        this.statusName = statusName;
    }

    public String statusName() { return statusName; }
}
