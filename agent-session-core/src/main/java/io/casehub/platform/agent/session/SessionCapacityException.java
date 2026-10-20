package io.casehub.platform.agent.session;

public class SessionCapacityException extends RuntimeException {
    public SessionCapacityException(String message) {
        super(message);
    }
    public SessionCapacityException(String message, Throwable cause) {
        super(message, cause);
    }
}
