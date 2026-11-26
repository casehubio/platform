package io.casehub.yaml.step.catalog;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TemplateMatchSpecTest {

    @Test
    void templateMatchSpec_construction() {
        var handler = List.<ResolvedStep>of(
            new ResolvedStep.PluginStep("s1", "action", Map.of(), Map.of()));
        var spec = new TemplateMatchSpec(
            "protocol:review", "${response.text}",
            Map.of("vote", handler), List.of());
        assertEquals("protocol:review", spec.namespace());
        assertEquals("${response.text}", spec.inputExpression());
        assertEquals(1, spec.handlers().size());
        assertTrue(spec.unmatchedHandler().isEmpty());
    }

    @Test
    void templateMatchSpec_nullNamespace_throws() {
        assertThrows(NullPointerException.class, () ->
            new TemplateMatchSpec(null, "${x}", Map.of("a", List.of()), List.of()));
    }

    @Test
    void templateMatchSpec_nullUnmatched_defaultsToEmpty() {
        var spec = new TemplateMatchSpec("ns", "${x}", Map.of("a", List.of()), null);
        assertTrue(spec.unmatchedHandler().isEmpty());
    }

    @Test
    void matchStep_valueMode_factory() {
        var step = ResolvedStep.MatchStep.valueMode(
            "m1", "${expr}", List.of(), Map.of());
        assertFalse(step.isTemplateMode());
        assertEquals("${expr}", step.scrutinee());
        assertNull(step.templateSpec());
    }

    @Test
    void matchStep_templateMode_factory() {
        var spec = new TemplateMatchSpec("ns", "${x}", Map.of("a", List.of()), List.of());
        var step = ResolvedStep.MatchStep.templateMode("m1", spec, Map.of());
        assertTrue(step.isTemplateMode());
        assertNull(step.scrutinee());
        assertNotNull(step.templateSpec());
    }

    @Test
    void matchStep_valueMode_nullScrutinee_throws() {
        assertThrows(NullPointerException.class, () ->
            ResolvedStep.MatchStep.valueMode("m1", null, List.of(), Map.of()));
    }

    @Test
    void matchStep_templateMode_nullSpec_throws() {
        assertThrows(NullPointerException.class, () ->
            ResolvedStep.MatchStep.templateMode("m1", null, Map.of()));
    }

    @Test
    void reservedCaptureNames_includes_tmatch() {
        assertTrue(StepKeywords.RESERVED_CAPTURE_NAMES.contains("_tmatch"));
    }
}
