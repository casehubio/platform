package io.casehub.platform.agent.guardrail;

public class GuardrailException extends RuntimeException {
    public GuardrailException(String reason) {
        super(reason);
    }
}
