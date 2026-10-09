package io.casehub.yaml.step.expand;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SourceLocationMap {
    private final Map<String, String> generatedToOriginal = new ConcurrentHashMap<>();

    void record(String generatedName, String parentContext) {
        generatedToOriginal.put(generatedName, parentContext);
    }

    public boolean isGenerated(String name) {
        return generatedToOriginal.containsKey(name);
    }

    public Optional<String> originalContext(String generatedName) {
        return Optional.ofNullable(generatedToOriginal.get(generatedName));
    }
}
