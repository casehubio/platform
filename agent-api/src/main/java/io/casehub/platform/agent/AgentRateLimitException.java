package io.casehub.platform.agent;

public class AgentRateLimitException extends RuntimeException {

    private final double permitsPerSecond;
    private final long retryAfterMillis;

    public AgentRateLimitException(double permitsPerSecond) {
        super("Agent rate limit exceeded (" + permitsPerSecond + " permits/sec)");
        this.permitsPerSecond = permitsPerSecond;
        this.retryAfterMillis = permitsPerSecond > 0
                ? (long) Math.ceil(1000.0 / permitsPerSecond)
                : 1000L;
    }

    public double permitsPerSecond() {
        return permitsPerSecond;
    }

    public long retryAfterMillis() {
        return retryAfterMillis;
    }
}
