package io.casehub.yaml.core.annotation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InjectionBindingTest {

    @Test
    void extractBindingsFromAnnotationMap() {
        var inject1 = new Annotation("Inject", List.of(
            new AnnotationArg.Positional("AgentProvider"),
            new AnnotationArg.Named("qualifier", "claude")));
        var inject2 = new Annotation("Inject", List.of(
            new AnnotationArg.Positional("CaseMemoryStore")));
        var map = new AnnotationMap(List.of(inject1, inject2), Map.of());

        var content = new LinkedHashMap<String, Object>();
        content.put("claude", "agent");
        content.put("memory", "store");
        content.put("name", "Test");

        var bindings = InjectionBinding.extractAll(map, content);
        assertEquals(2, bindings.size());

        assertEquals("claude", bindings.get(0).variableName());
        assertEquals("agent", bindings.get(0).typeHint());
        assertEquals("AgentProvider", bindings.get(0).typeName());
        assertEquals("claude", bindings.get(0).qualifiers().get("qualifier"));

        assertEquals("memory", bindings.get(1).variableName());
        assertEquals("store", bindings.get(1).typeHint());
        assertEquals("CaseMemoryStore", bindings.get(1).typeName());
        assertTrue(bindings.get(1).qualifiers().isEmpty());
    }

    @Test
    void noInjectAnnotationsReturnsEmpty() {
        var desc = new Annotation("description", List.of(new AnnotationArg.Positional("test")));
        var map = new AnnotationMap(List.of(desc), Map.of());
        var bindings = InjectionBinding.extractAll(map, Map.of("name", "Test"));
        assertTrue(bindings.isEmpty());
    }

    @Test
    void resolverInterface() {
        InjectionResolver resolver = (typeName, qualifiers) -> "resolved:" + typeName;
        assertEquals("resolved:AgentProvider", resolver.resolve("AgentProvider", Map.of()));
    }

    @Test
    void knownKeysNotTreatedAsBindings() {
        var inject = new Annotation("Inject", List.of(
            new AnnotationArg.Positional("AgentProvider")));
        var map = new AnnotationMap(List.of(inject), Map.of());

        var content = new LinkedHashMap<String, Object>();
        content.put("name", "Test");
        content.put("claude", "agent");
        content.put("steps", List.of());

        var bindings = InjectionBinding.extractAll(map, content);
        assertEquals(1, bindings.size());
        assertEquals("claude", bindings.get(0).variableName());
    }
}
