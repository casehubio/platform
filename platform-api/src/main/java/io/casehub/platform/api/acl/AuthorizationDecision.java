package io.casehub.platform.api.acl;

public record AuthorizationDecision(ConsentOutcome outcome, String reason) {

    public boolean approved() {
        return outcome == ConsentOutcome.APPROVED;
    }

    public static AuthorizationDecision approve() {
        return new AuthorizationDecision(ConsentOutcome.APPROVED, null);
    }

    public static AuthorizationDecision deny(String reason) {
        return new AuthorizationDecision(ConsentOutcome.DENIED, reason);
    }

    public static AuthorizationDecision needsConfirmation(String reason) {
        return new AuthorizationDecision(ConsentOutcome.NEEDS_CONFIRMATION, reason);
    }

    public static AuthorizationDecision needsApproval(String reason) {
        return new AuthorizationDecision(ConsentOutcome.NEEDS_APPROVAL, reason);
    }
}
