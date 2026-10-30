package io.casehub.yaml.step.eval;

import io.casehub.yaml.step.catalog.ResolvedStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FusedPipelineStepTest {

    @Test
    void fusedPipelineStepIsResolvedStep() {
        var descriptor = new ResolvedStep.FusedStageDescriptor(
            "denoise", "audio-denoise", Map.of(), Map.of(),
            "input", (params, services) -> null);
        var fused = new ResolvedStep.FusedPipelineStep(
            "transcript", List.of(descriptor), Map.of());
        assertInstanceOf(ResolvedStep.class, fused);
        assertEquals("transcript", fused.name());
        assertEquals(1, fused.stages().size());
    }

    @Test
    void fusedPipelineStepPreservesStageOrder() {
        var s1 = new ResolvedStep.FusedStageDescriptor("download", "download", Map.of(), Map.of(), "input", (p, s) -> null);
        var s2 = new ResolvedStep.FusedStageDescriptor("denoise", "denoise", Map.of(), Map.of(), "input", (p, s) -> null);
        var s3 = new ResolvedStep.FusedStageDescriptor("transcribe", "transcribe", Map.of(), Map.of(), "input", (p, s) -> null);
        var fused = new ResolvedStep.FusedPipelineStep("transcribe", List.of(s1, s2, s3), Map.of());
        assertEquals(3, fused.stages().size());
        assertEquals("download", fused.stages().get(0).stepName());
        assertEquals("denoise", fused.stages().get(1).stepName());
        assertEquals("transcribe", fused.stages().get(2).stepName());
    }

    @Test
    void fusedStageDescriptorCarriesDecorators() {
        var descriptor = new ResolvedStep.FusedStageDescriptor(
            "step", "action", Map.of("key", "value"),
            Map.of("timeout", "30s"), "input", (p, s) -> null);
        assertEquals(Map.of("timeout", "30s"), descriptor.decorators());
        assertEquals(Map.of("key", "value"), descriptor.params());
    }

    @Test
    void fusedPipelineStepDecoratorsAreChainLevel() {
        var s1 = new ResolvedStep.FusedStageDescriptor("s1", "a1", Map.of(), Map.of(), "input", (p, s) -> null);
        var fused = new ResolvedStep.FusedPipelineStep("s1", List.of(s1), Map.of("retry", 3));
        assertEquals(Map.of("retry", 3), fused.decorators());
    }
}
