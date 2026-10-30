package io.casehub.yaml.step.eval;

public final class FusedChainException extends RuntimeException {

    private final String failedStepName;
    private final String failedActionName;
    private final int failedStepIndex;
    private final String chainName;

    public FusedChainException(String failedStepName, String failedActionName,
                               int failedStepIndex, String chainName, Throwable cause) {
        super("Fused chain '" + chainName + "' failed at step " + failedStepIndex
              + " (" + failedStepName + " / " + failedActionName + "): " + cause.getMessage(),
              cause);
        this.failedStepName   = failedStepName;
        this.failedActionName = failedActionName;
        this.failedStepIndex  = failedStepIndex;
        this.chainName        = chainName;
    }

    public String failedStepName()   { return failedStepName; }
    public String failedActionName() { return failedActionName; }
    public int failedStepIndex()     { return failedStepIndex; }
    public String chainName()        { return chainName; }
}
