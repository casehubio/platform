package io.casehub.yaml.core.foreach;

import java.util.List;

@FunctionalInterface
public interface IterationValueExpander {
    List<String> expand(String resolvedValue, String groupContext);
}
