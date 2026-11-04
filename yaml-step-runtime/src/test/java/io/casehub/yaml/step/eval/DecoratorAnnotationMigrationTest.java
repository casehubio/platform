package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.annotation.Annotation;
import io.casehub.yaml.core.annotation.AnnotationArg;
import io.casehub.yaml.core.annotation.AnnotationMap;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import io.casehub.yaml.step.catalog.Walker;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DecoratorAnnotationMigrationTest {

    private static Definition defn(String name) {
        return new Definition(name, null, Map.of(), Map.of(), Portability.JAVA,
                (params, services) -> Result.of(Map.of()), null);
    }

    private final PluginRegistry registry = new CompositePluginRegistry() {{
        register(defn("rest-request"));
        register(defn("agent-invoke"));
    }};

    @Test
    void annotationTimeoutTakesPrecedenceOverKey() {
        var step = new LinkedHashMap<String, Object>();
        step.put("rest-request", Map.of("url", "/api"));
        step.put("timeout", "10s");
        var steps = List.<Map<String, Object>>of(step);

        var timeout = new Annotation("timeout", List.of(new AnnotationArg.Positional("5s")));
        var annotations = new AnnotationMap(List.of(), Map.of("0", List.of(timeout)));

        var resolved = Walker.resolve(steps, registry, annotations);
        assertEquals("5s", resolved.get(0).decorators().get("timeout"));
    }

    @Test
    void annotationOnlyWithNoKey() {
        var steps = List.of(
            Map.<String, Object>of("rest-request", Map.of("url", "/api")));
        var retry = new Annotation("retry", List.of(new AnnotationArg.Positional("3")));
        var annotations = new AnnotationMap(List.of(), Map.of("0", List.of(retry)));

        var resolved = Walker.resolve(steps, registry, annotations);
        assertEquals(3, resolved.get(0).decorators().get("retry"));
    }

    @Test
    void nonMigratedAnnotationNotInjectedAsDecorator() {
        var steps = List.of(
            Map.<String, Object>of("rest-request", Map.of("url", "/api")));
        var desc = new Annotation("description", List.of(new AnnotationArg.Positional("test")));
        var annotations = new AnnotationMap(List.of(), Map.of("0", List.of(desc)));

        var resolved = Walker.resolve(steps, registry, annotations);
        assertFalse(resolved.get(0).decorators().containsKey("description"));
        assertEquals(1, resolved.get(0).annotations().size());
    }

    @Test
    void backgroundAnnotationNoArgs() {
        var steps = List.of(
            Map.<String, Object>of("rest-request", Map.of("url", "/api")));
        var bg = new Annotation("background", List.of());
        var annotations = new AnnotationMap(List.of(), Map.of("0", List.of(bg)));

        var resolved = Walker.resolve(steps, registry, annotations);
        assertEquals(true, resolved.get(0).decorators().get("background"));
    }

    @Test
    void multipleAnnotationDecoratorsMerged() {
        var steps = List.of(
            Map.<String, Object>of("rest-request", Map.of("url", "/api")));
        var timeout = new Annotation("timeout", List.of(new AnnotationArg.Positional("5s")));
        var retry = new Annotation("retry", List.of(new AnnotationArg.Positional("3")));
        var annotations = new AnnotationMap(List.of(), Map.of("0", List.of(timeout, retry)));

        var resolved = Walker.resolve(steps, registry, annotations);
        assertEquals("5s", resolved.get(0).decorators().get("timeout"));
        assertEquals(3, resolved.get(0).decorators().get("retry"));
    }
}
