package io.casehub.yaml.step.eval;

import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.plugin.api.StreamingStep;
import io.casehub.yaml.plugin.api.StreamingStepFactory;
import io.casehub.yaml.plugin.api.StreamingTypeInfo;
import io.casehub.yaml.step.catalog.ResolvedStep;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FusionPassTest {

    private static Definition streamingDef(String name, Class<?> in, Class<?> out) {
        StreamingStepFactory<Object, Object> factory =
            (params, svc) -> new StreamingStep<>() {
                @Override public Class<Object> inputType() { return (Class<Object>) in; }
                @Override public Class<Object> outputType() { return (Class<Object>) out; }
                @Override public Object apply(Object input) { return input; }
            };
        return new Definition(name, name, Map.of(), Map.of(), Portability.JAVA,
            (p, s) -> Result.of(Map.of()), "steps",
            Optional.of(new StreamingTypeInfo(in, out, "input", factory)));
    }

    private static Definition nonStreamingDef(String name) {
        return new Definition(name, name, Map.of(), Map.of(), Portability.JAVA,
            (p, s) -> Result.of(Map.of()), "steps");
    }

    private static TestPluginRegistry registry(Definition... defs) {
        var r = new TestPluginRegistry();
        for (Definition d : defs) r.register(d);
        return r;
    }

    @Test
    void fusesConsecutiveCompatibleStreamingSteps() {
        var reg = registry(
            streamingDef("download", String.class, byte[].class),
            streamingDef("denoise", byte[].class, byte[].class),
            streamingDef("transcribe", byte[].class, String.class));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("download", "download", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("denoise", "denoise", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("transcribe", "transcribe", Map.of(), Map.of()));

        List<ResolvedStep> fused = FusionPass.fuse(steps, reg);
        assertEquals(1, fused.size());
        assertInstanceOf(ResolvedStep.FusedPipelineStep.class, fused.get(0));
        var pipeline = (ResolvedStep.FusedPipelineStep) fused.get(0);
        assertEquals(3, pipeline.stages().size());
        assertEquals("transcribe", pipeline.name());
    }

    @Test
    void doesNotFuseIncompatibleTypes() {
        var reg = registry(
            streamingDef("step1", String.class, Integer.class),
            streamingDef("step2", String.class, String.class));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("s1", "step1", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("s2", "step2", Map.of(), Map.of()));

        List<ResolvedStep> fused = FusionPass.fuse(steps, reg);
        assertEquals(2, fused.size());
        assertInstanceOf(ResolvedStep.PluginStep.class, fused.get(0));
        assertInstanceOf(ResolvedStep.PluginStep.class, fused.get(1));
    }

    @Test
    void nonStreamingStepBreaksChain() {
        var reg = registry(
            streamingDef("step1", String.class, String.class),
            nonStreamingDef("step2"),
            streamingDef("step3", String.class, String.class));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("s1", "step1", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("s2", "step2", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("s3", "step3", Map.of(), Map.of()));

        List<ResolvedStep> fused = FusionPass.fuse(steps, reg);
        assertEquals(3, fused.size());
    }

    @Test
    void chainBreakingDecoratorPreventsFusion() {
        var reg = registry(
            streamingDef("step1", String.class, String.class),
            streamingDef("step2", String.class, String.class));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("s1", "step1", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("s2", "step2", Map.of(),
                Map.of("background", true)));

        List<ResolvedStep> fused = FusionPass.fuse(steps, reg);
        assertEquals(2, fused.size());
    }

    @Test
    void variableReferenceOnFirstStepBreaksFusion() {
        var reg = registry(
            streamingDef("step1", String.class, String.class),
            streamingDef("step2", String.class, String.class),
            streamingDef("step3", String.class, String.class));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("s1", "step1", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("s2", "step2", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("s3", "step3",
                Map.of("ref", "${result.s1.output}"), Map.of()));

        List<ResolvedStep> fused = FusionPass.fuse(steps, reg);
        assertTrue(fused.size() >= 2, "s1 should not be fused since it's referenced");
    }

    @Test
    void singleStreamingStepIsNotFused() {
        var reg = registry(streamingDef("step1", String.class, String.class));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("s1", "step1", Map.of(), Map.of()));

        List<ResolvedStep> fused = FusionPass.fuse(steps, reg);
        assertEquals(1, fused.size());
        assertInstanceOf(ResolvedStep.PluginStep.class, fused.get(0));
    }

    @Test
    void fusibleDecoratorsDoNotBreakChain() {
        var reg = registry(
            streamingDef("step1", String.class, String.class),
            streamingDef("step2", String.class, String.class));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("s1", "step1", Map.of(),
                Map.of("timeout", "30s")),
            new ResolvedStep.PluginStep("s2", "step2", Map.of(),
                Map.of("retry", Map.of("max", 3))));

        List<ResolvedStep> fused = FusionPass.fuse(steps, reg);
        assertEquals(1, fused.size());
        assertInstanceOf(ResolvedStep.FusedPipelineStep.class, fused.get(0));
    }

    @Test
    void barrierAwaitReferenceBreaksFusion() {
        var reg = registry(
            streamingDef("step1", String.class, String.class),
            streamingDef("step2", String.class, String.class));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("s1", "step1", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("s2", "step2", Map.of(), Map.of()),
            new ResolvedStep.BarrierStep("barrier", List.of("s1"), null, Map.of()));

        List<ResolvedStep> fused = FusionPass.fuse(steps, reg);
        assertTrue(fused.stream().anyMatch(s ->
            s instanceof ResolvedStep.PluginStep ps && "s1".equals(ps.name())),
            "s1 should remain unfused because barrier awaits it");
    }

    @Test
    void mixedStreamingAndNonStreamingPreservedCorrectly() {
        var reg = registry(
            streamingDef("a", String.class, String.class),
            streamingDef("b", String.class, String.class),
            nonStreamingDef("c"),
            streamingDef("d", String.class, String.class),
            streamingDef("e", String.class, String.class));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("a", "a", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("b", "b", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("c", "c", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("d", "d", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("e", "e", Map.of(), Map.of()));

        List<ResolvedStep> fused = FusionPass.fuse(steps, reg);
        assertEquals(3, fused.size());
        assertInstanceOf(ResolvedStep.FusedPipelineStep.class, fused.get(0));
        assertInstanceOf(ResolvedStep.PluginStep.class, fused.get(1));
        assertInstanceOf(ResolvedStep.FusedPipelineStep.class, fused.get(2));
    }

    private static class TestPluginRegistry implements PluginRegistry {
        private final Map<String, Definition> defs = new HashMap<>();

        @Override public void register(Definition definition) {
            defs.put(definition.name(), definition);
        }

        @Override public Optional<Definition> resolve(String actionName) {
            return Optional.ofNullable(defs.get(actionName));
        }

        @Override public Set<String> availableActions() {
            return defs.keySet();
        }
    }
}
