package io.casehub.platform.agent.guardrail;

public sealed interface GuardrailResult
        permits GuardrailResult.Pass, GuardrailResult.Fail, GuardrailResult.Reprompt {
    record Pass() implements GuardrailResult {}
    record Fail(String reason) implements GuardrailResult {}
    record Reprompt(String repromptMessage) implements GuardrailResult {}
}
