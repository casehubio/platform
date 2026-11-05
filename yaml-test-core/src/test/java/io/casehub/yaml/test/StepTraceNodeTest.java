package io.casehub.yaml.test;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StepTraceNodeTest {

    @Test
    void flattenProducesDepthFirstList() {
        var child1 = StepTraceNode.leaf(0, "step-a", "plugin", "agent-invoke",
                StepOutcome.SIMULATED, 5, Map.of("strategy", "key-lookup"));
        var child2 = StepTraceNode.leaf(1, "step-b", "plugin", "assertThat",
                StepOutcome.EXECUTED, 2, Map.of());
        var root = new StepTraceNode(0, "block", "block", null,
                StepOutcome.EXECUTED, 10, Map.of(), List.of(child1, child2));

        var flat = root.flatten();

        assertThat(flat).hasSize(3);
        assertThat(flat.get(0).depth()).isEqualTo(0);
        assertThat(flat.get(0).node()).isEqualTo(root);
        assertThat(flat.get(1).depth()).isEqualTo(1);
        assertThat(flat.get(1).node()).isEqualTo(child1);
        assertThat(flat.get(2).depth()).isEqualTo(1);
        assertThat(flat.get(2).node()).isEqualTo(child2);
    }

    @Test
    void leafHasEmptyChildren() {
        var leaf = StepTraceNode.leaf(0, "s", "plugin", "act",
                StepOutcome.EXECUTED, 1, Map.of());
        assertThat(leaf.children()).isEmpty();
    }

    @Test
    void stepCountRecursive() {
        var child = StepTraceNode.leaf(0, "s", "plugin", "act",
                StepOutcome.EXECUTED, 1, Map.of());
        var root = new StepTraceNode(0, "b", "block", null,
                StepOutcome.EXECUTED, 5, Map.of(), List.of(child));
        assertThat(root.stepCount()).isEqualTo(2);
    }

    @Test
    void nestedFlattenPreservesOrder() {
        var grandchild = StepTraceNode.leaf(0, "gc", "plugin", "act",
                StepOutcome.EXECUTED, 1, Map.of());
        var child = new StepTraceNode(0, "c", "block", null,
                StepOutcome.EXECUTED, 3, Map.of(), List.of(grandchild));
        var root = new StepTraceNode(0, "r", "playbook", null,
                StepOutcome.EXECUTED, 5, Map.of(), List.of(child));

        var flat = root.flatten();
        assertThat(flat).hasSize(3);
        assertThat(flat.get(0).depth()).isEqualTo(0);
        assertThat(flat.get(1).depth()).isEqualTo(1);
        assertThat(flat.get(2).depth()).isEqualTo(2);
    }

    @Test
    void detailMapIsDefensivelyCopied() {
        var mutable = new java.util.HashMap<String, Object>();
        mutable.put("key", "val");
        var node = StepTraceNode.leaf(0, "s", "plugin", "act",
                StepOutcome.EXECUTED, 1, mutable);
        mutable.put("extra", "should not appear");
        assertThat(node.detail()).doesNotContainKey("extra");
    }
}
