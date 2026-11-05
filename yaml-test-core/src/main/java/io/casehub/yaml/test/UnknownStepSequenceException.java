package io.casehub.yaml.test;

import java.util.Set;

public class UnknownStepSequenceException extends RuntimeException {
    private final String alias;
    private final String stepName;
    private final Set<String> availableSequences;

    public UnknownStepSequenceException(String alias, String stepName,
                                         Set<String> availableSequences) {
        super("Unknown step sequence '" + alias + "." + stepName
              + "'. Available: " + availableSequences);
        this.alias = alias;
        this.stepName = stepName;
        this.availableSequences = Set.copyOf(availableSequences);
    }

    public String alias() { return alias; }
    public String stepName() { return stepName; }
    public Set<String> availableSequences() { return availableSequences; }
}
