package io.casehub.platform.agent.guardrail;

import io.casehub.platform.agent.AgentSessionConfig;

@FunctionalInterface
public interface InputGuardrail {
    GuardrailResult validate(AgentSessionConfig config);
}
