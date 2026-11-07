package io.casehub.platform.simulation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    @Test
    void advanceStateMachinesAdvancesAllRegisteredSelectors() {
        var phases1 = Map.<String, List<InvocationRecord<Object, Object>>>of(
                "a", List.of(new InvocationRecord<>("t1", null, (Object) "in", (Object) "out-a1", Instant.now())),
                "b", List.of(new InvocationRecord<>("t1", null, (Object) "in", (Object) "out-b1", Instant.now())));
        var sm1 = new CorpusSelectorConfig.StateMachine("a",
                                                        Map.of("a", Map.of("step-one", "b")), phases1);

        var phases2 = Map.<String, List<InvocationRecord<Object, Object>>>of(
                "x", List.of(new InvocationRecord<>("t1", null, (Object) "in", (Object) "out-x", Instant.now())),
                "y", List.of(new InvocationRecord<>("t1", null, (Object) "in", (Object) "out-y", Instant.now())));
        var sm2 = new CorpusSelectorConfig.StateMachine("x",
                                                        Map.of("x", Map.of("step-one", "y")), phases2);

        var config = MapSimulationConfig.builder()
                                        .strategy("spi.one", "sequential")
                                        .corpusSelection("spi.one", sm1)
                                        .strategy("spi.two", "sequential")
                                        .corpusSelection("spi.two", sm2)
                                        .build();
        var runtime = new SimulationRuntime(config, new NoOpSimulationCorpus<>());

        runtime.strategyFor("spi.one");
        runtime.strategyFor("spi.two");

        assertThat(runtime.stateMachineSelector("spi.one").get().activePhase()).isEqualTo("a");
        assertThat(runtime.stateMachineSelector("spi.two").get().activePhase()).isEqualTo("x");

        runtime.advanceStateMachines("step-one");

        assertThat(runtime.stateMachineSelector("spi.one").get().activePhase()).isEqualTo("b");
        assertThat(runtime.stateMachineSelector("spi.two").get().activePhase()).isEqualTo("y");
    }

    @Test
    void advanceStateMachinesSkipsNonStateMachineSelectors() {
        var smPhases = Map.<String, List<InvocationRecord<Object, Object>>>of(
                "a", List.of(new InvocationRecord<>("t1", null, (Object) "in", (Object) "out-a", Instant.now())),
                "b", List.of(new InvocationRecord<>("t1", null, (Object) "in", (Object) "out-b", Instant.now())));
        var smConfig = new CorpusSelectorConfig.StateMachine("a",
                                                             Map.of("a", Map.of("go", "b")), smPhases);

        var lcPhases = Map.<String, List<InvocationRecord<Object, Object>>>of(
                "p", List.of(new InvocationRecord<>("t1", null, (Object) "in", (Object) "out-p", Instant.now())),
                "q", List.of(new InvocationRecord<>("t1", null, (Object) "in", (Object) "out-q", Instant.now())));
        var lcConfig = new CorpusSelectorConfig.LookupCount(
                List.of(new CorpusSelectorConfig.CountPhase("p", 2),
                        new CorpusSelectorConfig.CountPhase("q", 0)), lcPhases);

        var config = MapSimulationConfig.builder()
                                        .strategy("sm.spi", "sequential")
                                        .corpusSelection("sm.spi", smConfig)
                                        .strategy("lc.spi", "sequential")
                                        .corpusSelection("lc.spi", lcConfig)
                                        .build();
        var runtime = new SimulationRuntime(config, new NoOpSimulationCorpus<>());

        runtime.strategyFor("sm.spi");
        runtime.strategyFor("lc.spi");

        runtime.advanceStateMachines("go");

        assertThat(runtime.stateMachineSelector("sm.spi").get().activePhase()).isEqualTo("b");
        assertThat(runtime.stateMachineSelector("lc.spi")).isEmpty();
    }


}
