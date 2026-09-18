package io.casehub.platform.agent.simulation;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.simulation.CorpusSeed;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AgentCorpus {

    private AgentCorpus() {}

    public static CorpusSeed<AgentSimulationInput, List<AgentEvent>> invoke(String tenancyId) {
        return new CorpusSeed<AgentSimulationInput, List<AgentEvent>>(AgentProviderQN.INVOKE, tenancyId)
                .withKeyExtractor(SimulatedAgentBackend.defaultKeyExtractor());
    }

    public static AgentSimulationInput input(String systemPrompt, String userPrompt) {
        return new AgentSimulationInput(systemPrompt, userPrompt, null);
    }

    public static List<AgentEvent> textResponse(String text) {
        return List.of(new AgentEvent.TextDelta(text));
    }

    public static Function<String, String> llmFunction(AgentProvider agentProvider) {
        return prompt -> {
            var events = agentProvider.invoke(
                            AgentSessionConfig.of("You are a test data generator.", prompt))
                    .collect().asList().await().indefinitely();
            return events.stream()
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect(Collectors.joining());
        };
    }
}
