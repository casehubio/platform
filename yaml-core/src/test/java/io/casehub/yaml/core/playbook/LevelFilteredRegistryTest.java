package io.casehub.yaml.core.playbook;

import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelFilteredRegistryTest {

    @Test
    void filtersByLevelCapabilities() {
        var base = new SimplePluginRegistry();
        base.register(Definition.of("log").execute((p, s) -> Result.of(Map.of())).capability("steps").build());
        base.register(Definition.of("par").execute((p, s) -> Result.of(Map.of())).capability("concurrency").build());

        var levels = new MapPlaybookLevelRegistry();
        var filtered = LevelFilteredRegistry.forLevel(base, levels, "foundational");

        assertTrue(filtered.resolve("log").isPresent());
        assertFalse(filtered.resolve("par").isPresent());
        assertTrue(filtered.availableActions().contains("log"));
        assertFalse(filtered.availableActions().contains("par"));
    }

    @Test
    void intermediateIncludesConcurrency() {
        var base = new SimplePluginRegistry();
        base.register(Definition.of("log").execute((p, s) -> Result.of(Map.of())).capability("steps").build());
        base.register(Definition.of("par").execute((p, s) -> Result.of(Map.of())).capability("concurrency").build());

        var levels = new MapPlaybookLevelRegistry();
        var filtered = LevelFilteredRegistry.forLevel(base, levels, "intermediate");

        assertTrue(filtered.resolve("log").isPresent());
        assertTrue(filtered.resolve("par").isPresent());
    }

    @Test
    void unknownLevelThrows() {
        var base = new SimplePluginRegistry();
        var levels = new MapPlaybookLevelRegistry();
        assertThrows(IllegalArgumentException.class, () ->
                LevelFilteredRegistry.forLevel(base, levels, "nonexistent"));
    }

    @Test
    void availableActionsFiltered() {
        var base = new SimplePluginRegistry();
        base.register(Definition.of("a").execute((p, s) -> Result.of(Map.of())).capability("steps").build());
        base.register(Definition.of("b").execute((p, s) -> Result.of(Map.of())).capability("orchestration").build());
        base.register(Definition.of("c").execute((p, s) -> Result.of(Map.of())).capability("decorators").build());

        var levels = new MapPlaybookLevelRegistry();
        var filtered = LevelFilteredRegistry.forLevel(base, levels, "foundational");

        assertEquals(Set.of("a"), filtered.availableActions());
    }

    private static class SimplePluginRegistry implements PluginRegistry {
        private final ConcurrentHashMap<String, Definition> defs = new ConcurrentHashMap<>();

        @Override
        public void register(Definition definition) { defs.put(definition.name(), definition); }

        @Override
        public Optional<Definition> unregister(String actionName) {
            return Optional.ofNullable(defs.remove(actionName));
        }

        @Override
        public Optional<Definition> resolve(String actionName) {
            return Optional.ofNullable(defs.get(actionName));
        }

        @Override
        public Set<String> availableActions() {
            return Set.copyOf(defs.keySet());
        }
    }
}
