package io.casehub.yaml.core.foreach;

import java.util.Arrays;
import java.util.List;

@FunctionalInterface
public interface IterationValueExpander {
    List<String> expand(String resolvedValue, String groupContext);

    static IterationValueExpander commaSplit() {
        return (value, ctx) -> Arrays.stream(value.split(","))
                                               .map(String::trim)
                                               .filter(s -> !s.isEmpty())
                                               .toList();
    }

}
