package io.casehub.yaml.plugin.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefinitionVariantTest {

    private static final Action NOOP = (p, s) -> Result.of(Map.of());

    @Test
    void variantsDefaultToEmpty() {
        var def = Definition.of("test").execute(NOOP).build();
        assertTrue(def.variants().isEmpty());
    }

    @Test
    void builderAccumulatesVariants() {
        var def = Definition.of("device")
                .absorbSiblings(true)
                .variant("register", Map.of("name", new Parameter(ParameterType.STRING, true, null, null, null, null)))
                .variant("query", Map.of("id", new Parameter(ParameterType.STRING, true, null, null, null, null)))
                .execute(NOOP)
                .build();
        assertEquals(2, def.variants().size());
        assertTrue(def.variants().get("register").get("name").required());
        assertTrue(def.variants().get("query").get("id").required());
    }

    @Test
    void sevenArgConstructorDefaultsVariantsToEmpty() {
        var def = new Definition("test", null, Map.of(), Map.of(),
                Portability.BOTH, NOOP, null);
        assertTrue(def.variants().isEmpty());
    }

    @Test
    void nineArgConstructorDefaultsVariantsToEmpty() {
        var def = new Definition("test", null, Map.of(), Map.of(),
                Portability.BOTH, NOOP, null, java.util.Optional.empty(), true);
        assertTrue(def.variants().isEmpty());
    }

    @Test
    void canonicalConstructorWithVariants() {
        var variants = Map.of(
                "register", Map.of("name", new Parameter(ParameterType.STRING, true, null, null, null, null)),
                "query", Map.of("id", new Parameter(ParameterType.STRING, true, null, null, null, null)));
        var def = new Definition("device", "desc", Map.of(), Map.of(),
                Portability.JAVA, NOOP, "steps", java.util.Optional.empty(), true, variants);
        assertEquals(2, def.variants().size());
    }

    @Test
    void variantsAreUnmodifiable() {
        var def = Definition.of("device")
                .variant("register", Map.of("name", new Parameter(ParameterType.STRING, true, null, null, null, null)))
                .execute(NOOP)
                .build();
        assertThrows(UnsupportedOperationException.class, () ->
                def.variants().put("new-op", Map.of()));
    }
}
