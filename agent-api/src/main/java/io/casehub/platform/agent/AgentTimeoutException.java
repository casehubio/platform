package io.casehub.platform.agent;

import java.time.Duration;

public class AgentTimeoutException extends RuntimeException {
    public AgentTimeoutException(Duration timeout) {
        super("Agent session exceeded wall-clock timeout of " + timeout);
    }
}
