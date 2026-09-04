package io.casehub.platform.api.capacity;

public record RedistributionDecision(RedistributionAction action,
                                      String reason) {

    public static RedistributionDecision none() {
        return new RedistributionDecision(RedistributionAction.NONE, null);
    }

    public static RedistributionDecision compress(String reason) {
        return new RedistributionDecision(RedistributionAction.COMPRESS, reason);
    }

    public static RedistributionDecision redistribute(String reason) {
        return new RedistributionDecision(RedistributionAction.REDISTRIBUTE, reason);
    }

    public static RedistributionDecision escalate(String reason) {
        return new RedistributionDecision(RedistributionAction.ESCALATE, reason);
    }
}
