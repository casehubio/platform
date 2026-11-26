package io.casehub.yaml.step.runner;

import io.casehub.yaml.plugin.api.Result;

import java.util.Map;

record RecoveryOutcome(boolean recovered, Result.Failure terminalFailure,
                       PlaybookRunResult subResult, Map<String, Object> outputVars) {

    RecoveryOutcome {
        outputVars = outputVars != null ? Map.copyOf(outputVars) : Map.of();
    }

    static RecoveryOutcome recoveredWithOutput(Map<String, Object> outputVars) {
        return new RecoveryOutcome(true, null, null, outputVars);
    }

    static RecoveryOutcome recovered(PlaybookRunResult subResult) {
        return new RecoveryOutcome(true, null, subResult, Map.of());
    }

    static RecoveryOutcome notRecovered(Result.Failure failure) {
        return new RecoveryOutcome(false, failure, null, Map.of());
    }
}
