package io.casehub.yaml.core.module;

import java.util.Map;

public interface ModuleBridge<T> {
    T fromSections(Map<String, Map<String, Object>> sections);
    Map<String, Map<String, Object>> toSections(T content);
    default SectionContentRewriter rewriter() { return null; }
    default Map<String, String> deriveOutputs(
            T expandedContent, String alias, Map<String, String> paramScope) {
        return Map.of();
    }
}
