package io.casehub.yaml.core.playbook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapPlaybookLevelRegistryTest {

    @Test
    void resolveByName() {
        var reg = new MapPlaybookLevelRegistry();
        var level = reg.resolve("foundational");
        assertTrue(level.isPresent());
        assertEquals(1, level.get().ordinal());
        assertTrue(level.get().capabilities().contains(PlaybookCapabilities.STEPS));
    }

    @Test
    void resolveByOrdinal() {
        var reg = new MapPlaybookLevelRegistry();
        var level = reg.resolveByOrdinal(1);
        assertTrue(level.isPresent());
        assertEquals("foundational", level.get().name());
    }

    @Test
    void allLevelsOrderedByOrdinal() {
        var reg = new MapPlaybookLevelRegistry();
        var all = reg.all();
        assertEquals(5, all.size());
        assertEquals("foundational", all.get(0).name());
        assertEquals("basic-patterns", all.get(1).name());
        assertEquals("intermediate", all.get(2).name());
        assertEquals("advanced", all.get(3).name());
        assertEquals("expert", all.get(4).name());
    }

    @Test
    void higherLevelsIncludeLowerCapabilities() {
        var reg = new MapPlaybookLevelRegistry();
        var basic = reg.resolve("basic-patterns").orElseThrow();
        assertTrue(basic.capabilities().contains(PlaybookCapabilities.STEPS));
        assertTrue(basic.capabilities().contains(PlaybookCapabilities.VARIABLE_RESOLUTION));
        assertTrue(basic.capabilities().contains(PlaybookCapabilities.CONTROL_FLOW));
        assertTrue(basic.capabilities().contains(PlaybookCapabilities.DECORATORS));
    }

    @Test
    void intermediateIncludesConcurrencyAndModules() {
        var reg = new MapPlaybookLevelRegistry();
        var intermediate = reg.resolve("intermediate").orElseThrow();
        assertTrue(intermediate.capabilities().contains(PlaybookCapabilities.CONCURRENCY));
        assertTrue(intermediate.capabilities().contains(PlaybookCapabilities.MODULES));
        assertTrue(intermediate.capabilities().contains(PlaybookCapabilities.STEPS));
    }

    @Test
    void advancedIncludesOrchestration() {
        var reg = new MapPlaybookLevelRegistry();
        var advanced = reg.resolve("advanced").orElseThrow();
        assertTrue(advanced.capabilities().contains(PlaybookCapabilities.ORCHESTRATION));
        assertTrue(advanced.capabilities().contains(PlaybookCapabilities.CORRELATION));
        assertTrue(advanced.capabilities().contains(PlaybookCapabilities.STATE_MACHINE));
    }

    @Test
    void expertIncludesAll() {
        var reg = new MapPlaybookLevelRegistry();
        var expert = reg.resolve("expert").orElseThrow();
        assertTrue(expert.capabilities().contains(PlaybookCapabilities.MCP_INVOKE));
        assertTrue(expert.capabilities().contains(PlaybookCapabilities.GRAPHQL_INVOKE));
        assertTrue(expert.capabilities().contains(PlaybookCapabilities.CODEGEN));
        assertTrue(expert.capabilities().contains(PlaybookCapabilities.STEPS));
    }

    @Test
    void resolveOrThrowOnUnknown() {
        var reg = new MapPlaybookLevelRegistry();
        var ex = assertThrows(IllegalArgumentException.class, () -> reg.resolveOrThrow("nonexistent"));
        assertTrue(ex.getMessage().contains("nonexistent"));
    }

    @Test
    void unknownNameReturnsEmpty() {
        var reg = new MapPlaybookLevelRegistry();
        assertTrue(reg.resolve("nonexistent").isEmpty());
    }

    @Test
    void unknownOrdinalReturnsEmpty() {
        var reg = new MapPlaybookLevelRegistry();
        assertTrue(reg.resolveByOrdinal(99).isEmpty());
    }
}
