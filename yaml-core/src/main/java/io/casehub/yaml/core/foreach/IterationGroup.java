package io.casehub.yaml.core.foreach;

import java.util.List;

public record IterationGroup(String as, Object in) {

    public List<Object> inAsList() {
        if (in instanceof List<?> list) { return List.copyOf(list); }
        if (in instanceof String s) { return List.of(s); }
        if (in == null) { return List.of(); }
        throw new IllegalArgumentException(
                "iterations.in must be a list or string, got: " + in.getClass());
    }
}
