package io.casehub.platform.agent.guardrail;

import io.casehub.platform.agent.AgentSessionConfig;

@FunctionalInterface
public interface OutputGuardrail {
    GuardrailResult validate(String response, AgentSessionConfig originalConfig);
}
