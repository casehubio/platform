package io.casehub.yaml.step.catalog;

import io.casehub.yaml.core.annotation.Annotation;
import io.casehub.yaml.core.annotation.AnnotationArg;
import io.casehub.yaml.core.annotation.AnnotationMap;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalkerAnnotationTest {

    private static Definition defn(String name) {
        return new Definition(name, null, Map.of(), Map.of(), Portability.JAVA,
                (params, services) -> Result.of(Map.of()), null);
    }

    private final PluginRegistry registry = new CompositePluginRegistry() {{
        register(defn("rest-request"));
        register(defn("agent-invoke"));
    }};

    @Test
    void stepReceivesAnnotationsFromMap() {
        var steps = List.of(
            Map.<String, Object>of("rest-request", Map.of("url", "/api"))
        );
        var timeout = new Annotation("timeout", List.of(new AnnotationArg.Positional("5s")));
        var annotations = new AnnotationMap(List.of(), Map.of("0", List.of(timeout)));

        var resolved = Walker.resolve(steps, registry, annotations);
        assertEquals(1, resolved.size());
        assertEquals(1, resolved.get(0).annotations().size());
        assertEquals("timeout", resolved.get(0).annotations().get(0).name());
    }

    @Test
    void stepWithNoAnnotationsHasEmptyList() {
        var steps = List.of(
            Map.<String, Object>of("rest-request", Map.of("url", "/api"))
        );
        var resolved = Walker.resolve(steps, registry, AnnotationMap.empty());
        assertEquals(1, resolved.size());
        assertTrue(resolved.get(0).annotations().isEmpty());
    }

    @Test
    void existingResolveMethodStillWorks() {
        var steps = List.of(
            Map.<String, Object>of("rest-request", Map.of("url", "/api"))
        );
        var resolved = Walker.resolve(steps, registry);
        assertEquals(1, resolved.size());
        assertTrue(resolved.get(0).annotations().isEmpty());
    }

    @Test
    void multipleStepsWithAnnotations() {
        var steps = List.of(
            Map.<String, Object>of("rest-request", Map.of("url", "/api")),
            Map.<String, Object>of("agent-invoke", Map.of("prompt", "hello"))
        );
        var timeout = new Annotation("timeout", List.of(new AnnotationArg.Positional("5s")));
        var retry = new Annotation("retry", List.of(new AnnotationArg.Positional("3")));
        var annotations = new AnnotationMap(List.of(),
            Map.of("0", List.of(timeout), "1", List.of(retry)));

        var resolved = Walker.resolve(steps, registry, annotations);
        assertEquals(2, resolved.size());
        assertEquals("timeout", resolved.get(0).annotations().get(0).name());
        assertEquals("retry", resolved.get(1).annotations().get(0).name());
    }

    @Test
    void annotationsOnSecondStepOnly() {
        var steps = List.of(
            Map.<String, Object>of("rest-request", Map.of("url", "/api")),
            Map.<String, Object>of("agent-invoke", Map.of("prompt", "hello"))
        );
        var audit = new Annotation("audit", List.of(new AnnotationArg.Positional("PII")));
        var annotations = new AnnotationMap(List.of(), Map.of("1", List.of(audit)));

        var resolved = Walker.resolve(steps, registry, annotations);
        assertTrue(resolved.get(0).annotations().isEmpty());
        assertEquals(1, resolved.get(1).annotations().size());
    }

    @Test
    void nestedBlockStepReceivesAnnotations() {
        var blockChildren = List.of(
                Map.<String, Object>of("rest-request", Map.of("url", "/api")),
                Map.<String, Object>of("agent-invoke", Map.of("prompt", "hello"))
                                   );
        var steps = List.of(
                Map.<String, Object>of("block", blockChildren)
                           );
        var timeout = new Annotation("timeout", List.of(new AnnotationArg.Positional("5s")));
        var retry   = new Annotation("retry", List.of(new AnnotationArg.Positional("3")));
        var annotations = new AnnotationMap(List.of(),
                                            Map.of("0.0", List.of(timeout), "0.1", List.of(retry)));

        var resolved = Walker.resolve(steps, registry, annotations);
        var block    = (ResolvedStep.BlockStep) resolved.get(0);
        assertEquals(1, block.steps().get(0).annotations().size());
        assertEquals("timeout", block.steps().get(0).annotations().get(0).name());
        assertEquals(1, block.steps().get(1).annotations().size());
        assertEquals("retry", block.steps().get(1).annotations().get(0).name());
    }

    @Test
    void nestedAnnotationsAcrossTryCatch() {
        var tryChildren = List.of(
                Map.<String, Object>of("rest-request", Map.of("url", "/api"))
                                 );
        var catchChildren = List.of(
                Map.<String, Object>of("agent-invoke", Map.of("prompt", "handle"))
                                   );
        var steps = List.of(
                Map.<String, Object>of("try", tryChildren, "catch", catchChildren)
                           );
        var timeout = new Annotation("timeout", List.of(new AnnotationArg.Positional("5s")));
        var retry   = new Annotation("retry", List.of(new AnnotationArg.Positional("3")));
        var annotations = new AnnotationMap(List.of(),
                                            Map.of("0.0", List.of(timeout), "0.1", List.of(retry)));

        var resolved = Walker.resolve(steps, registry, annotations);
        var tryCatch = (ResolvedStep.TryCatchFinallyStep) resolved.get(0);
        assertEquals(1, tryCatch.trySteps().get(0).annotations().size());
        assertEquals("timeout", tryCatch.trySteps().get(0).annotations().get(0).name());
        assertEquals(1, tryCatch.catchSteps().get(0).annotations().size());
        assertEquals("retry", tryCatch.catchSteps().get(0).annotations().get(0).name());
    }


}
