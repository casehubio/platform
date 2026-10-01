package io.casehub.yaml.core.orchestration;

public class IllegalTransitionException extends RuntimeException {

    public IllegalTransitionException(String machineName, Object from, Object to) {
        super("Invalid transition in '" + machineName + "': " + from + " → " + to);
    }

    public IllegalTransitionException(String machineName, Object from, String reason) {
        super("Transition rejected in '" + machineName + "' from " + from + ": " + reason);
    }
}
