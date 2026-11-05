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
}
