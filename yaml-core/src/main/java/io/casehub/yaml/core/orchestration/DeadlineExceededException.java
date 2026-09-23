package io.casehub.yaml.core.orchestration;

import java.time.Duration;

public class DeadlineExceededException extends RuntimeException {
    private final String scopeName;
    private final Duration deadline;

    public DeadlineExceededException(String scopeName, Duration deadline) {
        super("Deadline exceeded in scope '" + scopeName + "' after " + deadline);
        this.scopeName = scopeName;
        this.deadline = deadline;
    }

    public String scopeName() { return scopeName; }
    public Duration deadline() { return deadline; }
}
