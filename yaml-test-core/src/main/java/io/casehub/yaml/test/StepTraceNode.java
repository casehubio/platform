package io.casehub.yaml.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record StepTraceNode(
        int index,
        String name,
        String type,
        String action,
        StepOutcome outcome,
        long durationMs,
        Map<String, Object> detail,
        List<StepTraceNode> children) {

    public StepTraceNode {
        detail = detail != null ? Map.copyOf(detail) : Map.of();
        children = children != null ? List.copyOf(children) : List.of();
    }

    public static StepTraceNode leaf(int index, String name, String type,
                                      String action, StepOutcome outcome,
                                      long durationMs, Map<String, Object> detail) {
        return new StepTraceNode(index, name, type, action, outcome,
                                 durationMs, detail, List.of());
    }

    public record FlatEntry(int depth, StepTraceNode node) {}

    public List<FlatEntry> flatten() {
        List<FlatEntry> result = new ArrayList<>();
        flatten(this, 0, result);
        return result;
    }

    private static void flatten(StepTraceNode node, int depth, List<FlatEntry> result) {
        result.add(new FlatEntry(depth, node));
        for (var child : node.children) {
            flatten(child, depth + 1, result);
        }
    }

    public int stepCount() {
        int count = 1;
        for (var child : children) {
            count += child.stepCount();
        }
        return count;
    }
}
