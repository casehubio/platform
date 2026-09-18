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

import java.util.List;
import java.util.Optional;

public class SimulatedAgentBackend implements AgentBackend {

    private static final String QN_INVOKE = "agent-provider.invoke";

    private final SimulationRuntime simulation;

    public SimulatedAgentBackend(final SimulationRuntime simulation) {
        this.simulation = simulation;
    }

    @Override
    public String key() {
        return "simulated";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Multi<AgentEvent> invoke(final AgentSessionConfig config) {
        final Optional<SimulationStrategy<AgentSimulationInput, List<AgentEvent>>> strategy =
                simulation.strategyFor(QN_INVOKE);
        if (strategy.isEmpty()) {
            return Multi.createFrom().empty();
        }

        final AgentSimulationInput input = AgentSimulationInput.from(config);
        if (!strategy.get().canResolve(input)) {
            return Multi.createFrom().empty();
        }

        final List<AgentEvent> events = strategy.get().resolve(input);
        return Multi.createFrom().iterable(events);
    }

    @Override
    public AgentSession openSession(final AgentSessionInit init) {
        throw new UnsupportedOperationException("Multi-turn simulation not yet implemented");
    }

    public static KeyExtractor<AgentSimulationInput> defaultKeyExtractor() {
        return input -> stripNonDeterministic(input.systemPrompt())
                + "::" + stripNonDeterministic(input.userPrompt());
    }

    private static String stripNonDeterministic(final String text) {
        if (text == null) return "";
        return text
                .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<UUID>")
                .replaceAll("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}", "<TIMESTAMP>");
    }
}
