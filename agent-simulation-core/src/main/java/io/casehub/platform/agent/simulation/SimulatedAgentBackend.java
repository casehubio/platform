package io.casehub.platform.agent.simulation;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.simulation.KeyExtractor;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.SimulationStrategy;
import io.smallrye.mutiny.Multi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SimulatedAgentBackend implements AgentBackend {



    private final SimulationRuntime simulation;

    public SimulatedAgentBackend(final SimulationRuntime simulation) {
        this.simulation = simulation;
    }

    @Override
    public String key() {
        return "simulation";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Multi<AgentEvent> invoke(final AgentSessionConfig config) {
        if (simulation != null) {
            final Optional<SimulationStrategy<AgentSimulationInput, List<AgentEvent>>> strategy =
                    simulation.strategyFor(AgentProviderQN.INVOKE);
            if (strategy.isPresent()) {
                final AgentSimulationInput input = AgentSimulationInput.from(config);
                if (strategy.get().canResolve(input)) {
                    final List<?> raw = strategy.get().resolve(input);
                    final List<AgentEvent> events = coerceEvents(raw);
                    return Multi.createFrom().iterable(events);
                }
            }
        }
        return cannedResponse(config);
    }

    private Multi<AgentEvent> cannedResponse(AgentSessionConfig config) {
        String modelId = config.model() != null ? config.model() : "unknown";
        return Multi.createFrom().items(
                new AgentEvent.TextDelta("Simulated response from " + modelId),
                new AgentEvent.InvocationComplete(0, 0, 0, 0, 0, 0.0, 0L, 0L, null, 0, false, null, null)
        );
    }

    @Override
    public AgentSession openSession(final AgentSessionInit init) {
        return new SimulationAgentSession(init.model());
    }

    public static KeyExtractor<AgentSimulationInput> defaultKeyExtractor() {
        return input -> stripNonDeterministic(input.systemPrompt())
                + "::" + stripNonDeterministic(input.userPrompt());
    }

    @SuppressWarnings("unchecked")
    private static List<AgentEvent> coerceEvents(List<?> raw) {
        List<AgentEvent> result = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item instanceof AgentEvent event) {
                result.add(event);
            } else if (item instanceof Map<?, ?> map) {
                String type = String.valueOf(map.get("type"));
                if ("TextDelta".equals(type)) {
                    result.add(new AgentEvent.TextDelta(String.valueOf(map.get("text"))));
                } else if ("InvocationComplete".equals(type)) {
                    result.add(new AgentEvent.InvocationComplete(
                            0, 0, 0, 0, 0, 0.0, 0L, 0L, null, 0, false, null, null));
                }
            }
        }
        return result;
    }

    private static String stripNonDeterministic(final String text) {
        if (text == null) return "";
        return text
                .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<UUID>")
                .replaceAll("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}", "<TIMESTAMP>");
    }
}
