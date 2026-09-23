package io.casehub.yaml.core.orchestration;

public class SemaphoreReentrancyException extends RuntimeException {

    public SemaphoreReentrancyException(String name, String stepContext) {
        super("Semaphore '" + name + "' already held by step context '" + stepContext
              + "'. Single-permit semaphores do not support reentrancy.");
    }
}
