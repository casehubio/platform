package io.casehub.yaml.core.foreach;

import java.util.List;

public record IterationGroup(String as, Object in) {

    public IterationGroup {
        if (in != null && !(in instanceof String) && !(in instanceof List<?>)) {
            throw new IllegalArgumentException(
                    "iterations.in must be a list or string, got: " + in.getClass());
        }
    }

    public List<Object> inAsList() {
        if (in instanceof List<?> list) {return List.copyOf(list);}
        if (in instanceof String s) {return List.of(s);}
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public static java.util.Map<String, IterationGroup> fromBlock(java.util.Map<String, Object> block) {
        var groups = new java.util.LinkedHashMap<String, IterationGroup>();
        for (var entry : block.entrySet()) {
            if (entry.getValue() instanceof java.util.Map<?, ?> spec) {
                String as = (String) spec.get("as");
                Object in = spec.get("in");
                groups.put(entry.getKey(), new IterationGroup(as, in));
            }
        }
        return java.util.Collections.unmodifiableMap(groups);
    }
}
