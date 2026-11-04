package io.casehub.platform.agent;

public class AgentSessionLimitException extends RuntimeException {
    private final int limit;

    public AgentSessionLimitException(int limit) {
        super("Agent session limit reached (" + limit + " active sessions)");
        this.limit = limit;
    }

    public int limit() { return limit; }
}
