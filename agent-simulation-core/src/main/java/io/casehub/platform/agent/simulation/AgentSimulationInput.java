package io.casehub.platform.agent.simulation;

import io.casehub.platform.agent.AgentSessionConfig;

public record AgentSimulationInput(String systemPrompt, String userPrompt, String model) {

    public static AgentSimulationInput from(final AgentSessionConfig config) {
        return new AgentSimulationInput(config.systemPrompt(), config.userPrompt(), config.model());
    }
}
