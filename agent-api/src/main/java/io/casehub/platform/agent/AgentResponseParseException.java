package io.casehub.platform.agent;

public class AgentResponseParseException extends RuntimeException {

    private final String rawResponse;

    public AgentResponseParseException(String message, Throwable cause, String rawResponse) {
        super(message, cause);
        this.rawResponse = rawResponse;
    }

    public String rawResponse() {
        return rawResponse;
    }
}
