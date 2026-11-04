package io.casehub.yaml.core.annotation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationMapTest {

    @Test
    void annotationWithPositionalArgs() {
        var ann = new Annotation("timeout", List.of(new AnnotationArg.Positional("5s")));
        assertEquals("timeout", ann.name());
        assertEquals("5s", ann.stringArg(0));
        assertFalse(ann.hasArg("backoff"));
    }

    @Test
    void annotationWithNamedArgs() {
        var ann = new Annotation("retry", List.of(
            new AnnotationArg.Positional("3"),
            new AnnotationArg.Named("backoff", "exponential")));
        assertEquals("3", ann.stringArg(0));
        assertEquals("exponential", ann.namedArg("backoff"));
        assertTrue(ann.hasArg("backoff"));
    }

    @Test
    void annotationWithNoArgs() {
        var ann = new Annotation("background", List.of());
        assertEquals("background", ann.name());
        assertEquals(0, ann.args().size());
    }

    @Test
    void annotationMapForStep() {
        var timeout = new Annotation("timeout", List.of(new AnnotationArg.Positional("5s")));
        var map = new AnnotationMap(
            List.of(),
            Map.of("0", List.of(timeout)));
        assertEquals(1, map.forStep(0).size());
        assertEquals("timeout", map.forStep(0).get(0).name());
        assertTrue(map.hasStepAnnotation(0, "timeout"));
        assertFalse(map.hasStepAnnotation(1, "timeout"));
    }

    @Test
    void annotationMapDocumentLevel() {
        var desc = new Annotation("description", List.of(new AnnotationArg.Positional("My playbook")));
        var map = new AnnotationMap(List.of(desc), Map.of());
        assertEquals(1, map.documentAnnotations().size());
        assertTrue(map.forDocument("description").isPresent());
        assertFalse(map.forDocument("requires").isPresent());
    }

    @Test
    void emptyAnnotationMap() {
        var map = AnnotationMap.empty();
        assertTrue(map.documentAnnotations().isEmpty());
        assertTrue(map.forStep(0).isEmpty());
    }

    @Test
    void stringArgOutOfBoundsReturnsNull() {
        var ann = new Annotation("timeout", List.of(new AnnotationArg.Positional("5s")));
        assertNull(ann.stringArg(5));
        assertNull(ann.stringArg(-1));
    }

    @Test
    void namedArgMissingReturnsNull() {
        var ann = new Annotation("timeout", List.of(new AnnotationArg.Positional("5s")));
        assertNull(ann.namedArg("nonexistent"));
    }
}
