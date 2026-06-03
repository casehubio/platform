package io.casehub.platform.agent;

public class AgentSessionLimitException extends RuntimeException {
    public AgentSessionLimitException(int limit) {
        super("Agent session limit reached (" + limit + " active sessions)");
    }
}
