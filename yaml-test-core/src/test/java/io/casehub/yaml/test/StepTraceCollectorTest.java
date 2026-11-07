package io.casehub.yaml.test;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StepTraceCollectorTest {

    @Test
    void flatSequenceProducesRootChildren() {
        var collector = new StepTraceCollector();

        collector.enterStep(0, "step-a", "plugin");
        collector.exitStep("agent-invoke", StepOutcome.EXECUTED, Map.of());

        collector.enterStep(1, "step-b", "plugin");
        collector.exitStep("assertThat", StepOutcome.EXECUTED, Map.of());

        assertThat(collector.rootChildren()).hasSize(2);
        assertThat(collector.rootChildren().get(0).name()).isEqualTo("step-a");
        assertThat(collector.rootChildren().get(1).name()).isEqualTo("step-b");
    }

    @Test
    void nestedStepsProduceTree() {
        var collector = new StepTraceCollector();

        collector.enterStep(0, "if-block", "if-else");
        collector.enterStep(0, "inner-step", "plugin");
        collector.exitStep("agent-invoke", StepOutcome.EXECUTED, Map.of());
        collector.exitStep(null, StepOutcome.EXECUTED, Map.of("branch", "then"));

        assertThat(collector.rootChildren()).hasSize(1);
        var ifNode = collector.rootChildren().get(0);
        assertThat(ifNode.type()).isEqualTo("if-else");
        assertThat(ifNode.children()).hasSize(1);
        assertThat(ifNode.children().get(0).name()).isEqualTo("inner-step");
    }

    @Test
    void depthTracksNesting() {
        var collector = new StepTraceCollector();
        assertThat(collector.depth()).isEqualTo(0);

        collector.enterStep(0, "outer", "block");
        assertThat(collector.depth()).isEqualTo(1);

        collector.enterStep(0, "inner", "plugin");
        assertThat(collector.depth()).isEqualTo(2);

        collector.exitStep("act", StepOutcome.EXECUTED, Map.of());
        assertThat(collector.depth()).isEqualTo(1);

        collector.exitStep(null, StepOutcome.EXECUTED, Map.of());
        assertThat(collector.depth()).isEqualTo(0);
    }

    @Test
    void durationIsCaptured() {
        var collector = new StepTraceCollector();
        collector.enterStep(0, "s", "plugin");
        collector.exitStep("act", StepOutcome.EXECUTED, Map.of());

        assertThat(collector.rootChildren().get(0).durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void afterStepAdvancesStateMachineSelectorsOnSuccess() {
        var phases = Map.<String, java.util.List<io.casehub.platform.simulation.InvocationRecord<Object, Object>>>of(
                "pending", java.util.List.of(new io.casehub.platform.simulation.InvocationRecord<>("t1", null, (Object) "in", (Object) "pending-out", java.time.Instant.now())),
                "completed", java.util.List.of(new io.casehub.platform.simulation.InvocationRecord<>("t1", null, (Object) "in", (Object) "completed-out", java.time.Instant.now())));
        var smConfig = new io.casehub.platform.simulation.CorpusSelectorConfig.StateMachine("pending",
                                                                                            Map.of("pending", Map.of("submit-payment", "completed")), phases);

        var config = io.casehub.platform.simulation.MapSimulationConfig.builder()
                                                                       .strategy("payment.process", "sequential")
                                                                       .corpusSelection("payment.process", smConfig)
                                                                       .build();
        var runtime = new io.casehub.platform.simulation.SimulationRuntime(config, new io.casehub.platform.simulation.NoOpSimulationCorpus<>());
        runtime.strategyFor("payment.process");

        var collector = new StepTraceCollector(runtime);
        var step = new io.casehub.yaml.step.catalog.ResolvedStep.PluginStep(
                "submit-payment", "payment.process", Map.of(), Map.of());

        collector.beforeStep(step);
        collector.afterStep(step, io.casehub.yaml.plugin.api.Result.of(Map.of()), Map.of());

        assertThat(runtime.stateMachineSelector("payment.process").get().activePhase())
                .isEqualTo("completed");
    }

    @Test
    void afterStepDoesNotAdvanceOnFailure() {
        var phases = Map.<String, java.util.List<io.casehub.platform.simulation.InvocationRecord<Object, Object>>>of(
                "pending", java.util.List.of(new io.casehub.platform.simulation.InvocationRecord<>("t1", null, (Object) "in", (Object) "pending-out", java.time.Instant.now())),
                "completed", java.util.List.of(new io.casehub.platform.simulation.InvocationRecord<>("t1", null, (Object) "in", (Object) "completed-out", java.time.Instant.now())));
        var smConfig = new io.casehub.platform.simulation.CorpusSelectorConfig.StateMachine("pending",
                                                                                            Map.of("pending", Map.of("submit-payment", "completed")), phases);

        var config = io.casehub.platform.simulation.MapSimulationConfig.builder()
                                                                       .strategy("payment.process", "sequential")
                                                                       .corpusSelection("payment.process", smConfig)
                                                                       .build();
        var runtime = new io.casehub.platform.simulation.SimulationRuntime(config, new io.casehub.platform.simulation.NoOpSimulationCorpus<>());
        runtime.strategyFor("payment.process");

        var collector = new StepTraceCollector(runtime);
        var step = new io.casehub.yaml.step.catalog.ResolvedStep.PluginStep(
                "submit-payment", "payment.process", Map.of(), Map.of());

        collector.beforeStep(step);
        collector.afterStep(step, io.casehub.yaml.plugin.api.Result.failed("error"), Map.of());

        assertThat(runtime.stateMachineSelector("payment.process").get().activePhase())
                .isEqualTo("pending");
    }

    @Test
    void afterStepIgnoresNullStepName() {
        var phases = Map.<String, java.util.List<io.casehub.platform.simulation.InvocationRecord<Object, Object>>>of(
                "a", java.util.List.of(new io.casehub.platform.simulation.InvocationRecord<>("t1", null, (Object) "in", (Object) "out-a", java.time.Instant.now())),
                "b", java.util.List.of(new io.casehub.platform.simulation.InvocationRecord<>("t1", null, (Object) "in", (Object) "out-b", java.time.Instant.now())));
        var smConfig = new io.casehub.platform.simulation.CorpusSelectorConfig.StateMachine("a",
                                                                                            Map.of("a", Map.of("go", "b")), phases);

        var config = io.casehub.platform.simulation.MapSimulationConfig.builder()
                                                                       .strategy("spi.method", "sequential")
                                                                       .corpusSelection("spi.method", smConfig)
                                                                       .build();
        var runtime = new io.casehub.platform.simulation.SimulationRuntime(config, new io.casehub.platform.simulation.NoOpSimulationCorpus<>());
        runtime.strategyFor("spi.method");

        var collector = new StepTraceCollector(runtime);
        var step = new io.casehub.yaml.step.catalog.ResolvedStep.PluginStep(
                null, "spi.method", Map.of(), Map.of());

        collector.beforeStep(step);
        collector.afterStep(step, io.casehub.yaml.plugin.api.Result.of(Map.of()), Map.of());

        assertThat(runtime.stateMachineSelector("spi.method").get().activePhase())
                .isEqualTo("a");
    }

}
