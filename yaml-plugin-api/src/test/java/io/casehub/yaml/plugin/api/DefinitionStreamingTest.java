package io.casehub.yaml.plugin.api;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DefinitionStreamingTest {

    @Test
    void definitionWithoutStreamingTypeDefaultsToEmpty() {
        Definition def = new Definition("test", "desc",
            Map.of(), Map.of(), Portability.JAVA,
            (params, services) -> Result.of(Map.of()), "steps");
        assertEquals(Optional.empty(), def.streamingType());
    }

    @Test
    void definitionWithStreamingTypeCarriesMetadata() {
        StreamingStepFactory<String, Integer> factory =
            (params, services) -> new StreamingStep<>() {
                @Override public Class<String> inputType() { return String.class; }
                @Override public Class<Integer> outputType() { return Integer.class; }
                @Override public Integer apply(String s) { return s.length(); }
            };
        StreamingTypeInfo info = new StreamingTypeInfo(
            String.class, Integer.class, "input", factory);
        Definition def = Definition.of("test")
            .description("desc")
            .execute((params, services) -> Result.of(Map.of()))
            .streamingType(info)
            .build();
        assertTrue(def.streamingType().isPresent());
        assertEquals(String.class, def.streamingType().get().inputType());
        assertEquals(Integer.class, def.streamingType().get().outputType());
        assertEquals("input", def.streamingType().get().inputKey());
    }

    @Test
    void builderDefaultsStreamingTypeToEmpty() {
        Definition def = Definition.of("test")
            .description("desc")
            .execute((params, services) -> Result.of(Map.of()))
            .build();
        assertEquals(Optional.empty(), def.streamingType());
    }

    @Test
    void secondaryConstructorDefaultsStreamingTypeToEmpty() {
        Definition def = new Definition("test", "desc",
            Map.of(), Map.of(), Portability.JAVA,
            (params, services) -> Result.of(Map.of()), "steps");
        assertEquals(Optional.empty(), def.streamingType());
    }

    @Test
    void canonicalConstructorAcceptsStreamingType() {
        StreamingTypeInfo info = new StreamingTypeInfo(
            String.class, String.class, "text",
            (params, services) -> null);
        Definition def = new Definition("test", "desc",
            Map.of(), Map.of(), Portability.JAVA,
            (params, services) -> Result.of(Map.of()), "steps",
            Optional.of(info));
        assertTrue(def.streamingType().isPresent());
    }

    @Test
    void nullStreamingTypeDefaultsToEmpty() {
        Definition def = new Definition("test", "desc",
            Map.of(), Map.of(), Portability.JAVA,
            (params, services) -> Result.of(Map.of()), "steps",
            null);
        assertEquals(Optional.empty(), def.streamingType());
    }
}
