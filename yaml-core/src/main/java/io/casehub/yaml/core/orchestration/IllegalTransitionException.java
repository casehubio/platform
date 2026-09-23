package io.casehub.yaml.core.orchestration;

public class IllegalTransitionException extends RuntimeException {

    public IllegalTransitionException(String machineName, Enum<?> from, Enum<?> to) {
        super("Invalid transition in '" + machineName + "': " + from + " → " + to);
    }

    public IllegalTransitionException(String machineName, Enum<?> from, String reason) {
        super("Transition rejected in '" + machineName + "' from " + from + ": " + reason);
    }
}
