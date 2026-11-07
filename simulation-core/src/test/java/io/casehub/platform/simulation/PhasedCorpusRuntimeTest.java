package io.casehub.platform.simulation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PhasedCorpusRuntimeTest {

    private static final String QN = "test-spi.query";

    @Test
    void strategyUsesPhasedCorpusWhenCorpusSelectionConfigured() {
        var phases = Map.<String, List<InvocationRecord<Object, Object>>>of(
                "pending", List.of(new InvocationRecord<>("t1", "k1", (Object) "in", (Object) "pending-out", Instant.now())),
                "completed", List.of(new InvocationRecord<>("t1", "k1", (Object) "in", (Object) "completed-out", Instant.now())));
        var smConfig = new CorpusSelectorConfig.StateMachine("pending",
                Map.of("pending", Map.of("pay", "completed")), phases);

        var config = MapSimulationConfig.builder()
                .strategy(QN, "key-lookup")
                .corpusSelection(QN, smConfig)
                .build();
        var runtime = new SimulationRuntime(config, new NoOpSimulationCorpus<>());
        runtime.registerExtractor(QN, (Object input) -> "k1");

        var strategy = runtime.<Object, Object>strategyFor(QN);
        assertThat(strategy).isPresent();
        assertThat(strategy.get().resolve("anything")).isEqualTo("pending-out");

        runtime.stateMachineSelector(QN).ifPresent(sm -> sm.advance("pay"));
        assertThat(strategy.get().resolve("anything")).isEqualTo("completed-out");
    }

    @Test
    void lookupCountSelectorAdvancesViaOnResolved() {
        var phases = Map.<String, List<InvocationRecord<Object, Object>>>of(
                "a", List.of(new InvocationRecord<>("t1", null, (Object) "in", (Object) "out-a", Instant.now())),
                "b", List.of(new InvocationRecord<>("t1", null, (Object) "in", (Object) "out-b", Instant.now())));
        var lcConfig = new CorpusSelectorConfig.LookupCount(
                List.of(new CorpusSelectorConfig.CountPhase("a", 2),
                        new CorpusSelectorConfig.CountPhase("b", 0)), phases);

        var config = MapSimulationConfig.builder()
                .strategy(QN, "sequential")
                .corpusSelection(QN, lcConfig)
                .build();
        var runtime = new SimulationRuntime(config, new NoOpSimulationCorpus<>());

        var strategy = runtime.<Object, Object>strategyFor(QN);
        assertThat(strategy).isPresent();

        assertThat(strategy.get().resolve("x")).isEqualTo("out-a");
        assertThat(strategy.get().resolve("x")).isEqualTo("out-a");
        assertThat(strategy.get().resolve("x")).isEqualTo("out-b");
    }

    @Test
    void noCorpusSelectionUsesRegularCorpus() {
        var config = MapSimulationConfig.builder()
                .strategy(QN, "sequential")
                .build();
        var corpus = new NoOpSimulationCorpus<>();
        var runtime = new SimulationRuntime(config, corpus);

        assertThat(runtime.stateMachineSelector(QN)).isEmpty();
    }

    @Test
    void selectorSurvivesOverlayPushPop() {
        var phases = Map.<String, List<InvocationRecord<Object, Object>>>of(
                "a", List.of(new InvocationRecord<>("t1", null, (Object) "in", (Object) "out-a", Instant.now())),
                "b", List.of(new InvocationRecord<>("t1", null, (Object) "in", (Object) "out-b", Instant.now())));
        var smConfig = new CorpusSelectorConfig.StateMachine("a",
                Map.of("a", Map.of("go", "b")), phases);

        var config = MapSimulationConfig.builder()
                .strategy(QN, "sequential")
                .corpusSelection(QN, smConfig)
                .build();
        var runtime = new SimulationRuntime(config, new NoOpSimulationCorpus<>());

        var strategy1 = runtime.<Object, Object>strategyFor(QN);
        assertThat(strategy1.get().resolve("x")).isEqualTo("out-a");

        runtime.stateMachineSelector(QN).ifPresent(sm -> sm.advance("go"));

        var overlay = runtime.pushOverlay(MapSimulationConfig.of(Map.of("other.method", "seq")));
        runtime.popOverlay(overlay);

        var strategy2 = runtime.<Object, Object>strategyFor(QN);
        assertThat(strategy2.get().resolve("x")).isEqualTo("out-b");
    }

    @Test
    void canResolveDoesNotIncrementLookupCount() {
        var phases = Map.<String, List<InvocationRecord<Object, Object>>>of(
                "a", List.of(new InvocationRecord<>("t1", "k1", (Object) "in", (Object) "out-a", Instant.now())),
                "b", List.of(new InvocationRecord<>("t1", "k1", (Object) "in", (Object) "out-b", Instant.now())));
        var lcConfig = new CorpusSelectorConfig.LookupCount(
                List.of(new CorpusSelectorConfig.CountPhase("a", 1),
                        new CorpusSelectorConfig.CountPhase("b", 0)), phases);

        var config = MapSimulationConfig.builder()
                .strategy(QN, "key-lookup")
                .corpusSelection(QN, lcConfig)
                .build();
        var runtime = new SimulationRuntime(config, new NoOpSimulationCorpus<>());
        runtime.registerExtractor(QN, (Object input) -> "k1");

        var strategy = runtime.<Object, Object>strategyFor(QN);

        assertThat(strategy.get().canResolve("anything")).isTrue();
        assertThat(strategy.get().canResolve("anything")).isTrue();
        assertThat(strategy.get().canResolve("anything")).isTrue();
        assertThat(strategy.get().resolve("anything")).isEqualTo("out-a");
        assertThat(strategy.get().resolve("anything")).isEqualTo("out-b");
    }
}
