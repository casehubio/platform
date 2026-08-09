package io.casehub.platform.api.acl;

public record AuthorizationDecision(boolean approved, String reason) {

    public static AuthorizationDecision approve() {
        return new AuthorizationDecision(true, null);
    }

    public static AuthorizationDecision deny(String reason) {
        return new AuthorizationDecision(false, reason);
    }
}
