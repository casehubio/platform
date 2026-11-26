package io.casehub.yaml.step.runner;

import io.casehub.yaml.plugin.api.Result;

public interface FailureRecoveryHandler {
    boolean canRecover(Result.Failure failure);
    RecoveryAction diagnose(Result.Failure failure, RecoveryContext context);
}
