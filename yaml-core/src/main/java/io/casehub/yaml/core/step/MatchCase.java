package io.casehub.yaml.core.step;

import java.util.List;
import java.util.Map;

public record MatchCase(
        MatchPattern pattern,
        String guard,
        List<Map<String, Object>> steps) {

    public MatchCase {
        steps = List.copyOf(steps);
    }
}
