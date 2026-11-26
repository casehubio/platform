package io.casehub.yaml.step.runner;

import io.casehub.yaml.plugin.api.Result;

record RecoveryOutcome(boolean recovered, Result.Failure terminalFailure, PlaybookRunResult subResult) {

    static RecoveryOutcome recovered(PlaybookRunResult subResult) {
        return new RecoveryOutcome(true, null, subResult);
    }

    static RecoveryOutcome notRecovered(Result.Failure failure) {
        return new RecoveryOutcome(false, failure, null);
    }
}
