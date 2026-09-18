package io.casehub.platform.agent.simulation;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.NoOpSimulationCorpus;
import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedAgentBackendTest {

    private static final String QN_INVOKE = "agent-provider.invoke";

    @Test
    void keyReturnsSimulated() {
        final var runtime = createRuntime(Optional.empty(), false);
        final AgentBackend backend = new SimulatedAgentBackend(runtime);

        assertThat(backend.key()).isEqualTo("simulated");
    }

    @Test
    void invokeWithNoStrategyReturnsEmptyMulti() {
        final var runtime = createRuntime(Optional.empty(), false);
        final var backend = new SimulatedAgentBackend(runtime);

        final var events = backend.invoke(AgentSessionConfig.of("system", "hello"))
                .collect().asList().await().indefinitely();

        assertThat(events).isEmpty();
    }

    @Test
    void invokeWithStrategyReturnsEventsFromCorpus() {
        final List<AgentEvent> expectedEvents = List.of(
                new AgentEvent.TextDelta("Hello "),
                new AgentEvent.TextDelta("world!"));

        final var corpus = new InMemorySimulationCorpus<AgentSimulationInput, List<AgentEvent>>();
        corpus.seed(QN_INVOKE, List.of(
                new InvocationRecord<>("t1", null,
                        new AgentSimulationInput("sys", "hello", null),
                        expectedEvents,
                        Instant.now())));

        final var config = stubConfig(Optional.of("sequential"), false, Optional.empty());
        final var runtime = new SimulationRuntime(config, corpus);

        final var backend = new SimulatedAgentBackend(runtime);
        final var events = backend.invoke(AgentSessionConfig.of("sys", "hello"))
                .collect().asList().await().indefinitely();

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(AgentEvent.TextDelta.class);
        assertThat(((AgentEvent.TextDelta) events.get(0)).text()).isEqualTo("Hello ");
        assertThat(((AgentEvent.TextDelta) events.get(1)).text()).isEqualTo("world!");
    }

    @Test
    void invokeWhenCannotResolveReturnsEmptyMulti() {
        final var corpus = new InMemorySimulationCorpus<AgentSimulationInput, List<AgentEvent>>();
        final var config = stubConfig(Optional.of("key-lookup"), false, Optional.empty());
        final var runtime = new SimulationRuntime(config, corpus);
        runtime.registerExtractor(QN_INVOKE,
                (AgentSimulationInput input) -> input.userPrompt());

        final var backend = new SimulatedAgentBackend(runtime);
        final var events = backend.invoke(AgentSessionConfig.of("sys", "missing"))
                .collect().asList().await().indefinitely();

        assertThat(events).isEmpty();
    }

    @Test
    void openSessionThrowsUnsupported() {
        final var runtime = createRuntime(Optional.empty(), false);
        final var backend = new SimulatedAgentBackend(runtime);

        assertThatThrownBy(() -> backend.openSession(AgentSessionInit.of("sys")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void defaultKeyExtractorStripsUUIDs() {
        final var extractor = SimulatedAgentBackend.defaultKeyExtractor();

        final var input = new AgentSimulationInput(
                "System with id 550e8400-e29b-41d4-a716-446655440000",
                "Process case abc at 2026-01-15T10:30:00",
                null);

        final String key = extractor.extract(input);
        assertThat(key).contains("<UUID>");
        assertThat(key).contains("<TIMESTAMP>");
        assertThat(key).doesNotContain("550e8400");
        assertThat(key).doesNotContain("2026-01-15");
    }

    @Test
    void agentSimulationInputFromConfig() {
        final var config = AgentSessionConfig.of("system-prompt", "user-prompt", "claude-5");
        final var input = AgentSimulationInput.from(config);

        assertThat(input.systemPrompt()).isEqualTo("system-prompt");
        assertThat(input.userPrompt()).isEqualTo("user-prompt");
        assertThat(input.model()).isEqualTo("claude-5");
    }

    // --- helpers ---

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SimulationRuntime createRuntime(final Optional<String> strategy, final boolean capture) {
        return new SimulationRuntime(
                stubConfig(strategy, capture, Optional.empty()),
                new NoOpSimulationCorpus());
    }

    private static SimulationConfig stubConfig(final Optional<String> strategy,
                                               final boolean capture,
                                               final Optional<ExhaustionPolicy> exhaustion) {
        return new SimulationConfig() {
            @Override
            public Optional<String> strategyFor(final String qualifiedName) {
                return strategy;
            }

            @Override
            public boolean captureEnabled(final String qualifiedName) {
                return capture;
            }

            @Override
            public Optional<ExhaustionPolicy> exhaustionPolicy(final String qualifiedName) {
                return exhaustion;
            }
        };
    }
}
