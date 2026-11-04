package io.casehub.platform.agent;

import java.time.Duration;

public class AgentTimeoutException extends RuntimeException {
    private final Duration timeout;

    public AgentTimeoutException(Duration timeout) {
        super("Agent session exceeded wall-clock timeout of " + timeout);
        this.timeout = timeout;
    }

    public Duration timeout() { return timeout; }
}
