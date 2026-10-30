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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FusionPassEdgeCaseTest {

    // #11 is addressed by wiring fixes — tested implicitly via compilation
    // Here we add tests for #12-16

    // --- #12: Multi-param streaming step — verifies inputKey extraction ---

    @Test
    void multiParamStepUsesInputKeyNotFirstValue() {
        StreamingStepFactory<String, String> factory =
            (params, svc) -> new StreamingStep<>() {
                @Override public Class<String> inputType() { return String.class; }
                @Override public Class<String> outputType() { return String.class; }
                @Override public String apply(String input) { return "processed:" + input; }
            };
        var reg = registry(streamingDef("multi-param", String.class, String.class, "text", factory));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("s1", "multi-param",
                Map.of("model", "gpt-4", "text", "hello"), Map.of()));

        List<ResolvedStep> fused = FusionPass.fuse(steps, reg);
        // Single step can't fuse, but let's verify the inputKey is correct
        assertEquals(1, fused.size());

        // Build a 2-step chain to verify inputKey flows through fusion
        var reg2 = registry(
            streamingDef("step-a", String.class, String.class, "text", factory),
            streamingDef("step-b", String.class, String.class, "input", factory));

        List<ResolvedStep> steps2 = List.of(
            new ResolvedStep.PluginStep("a", "step-a",
                Map.of("model", "gpt-4", "text", "hello"), Map.of()),
            new ResolvedStep.PluginStep("b", "step-b", Map.of(), Map.of()));

        List<ResolvedStep> fused2 = FusionPass.fuse(steps2, reg2);
        assertEquals(1, fused2.size());
        var pipeline = (ResolvedStep.FusedPipelineStep) fused2.get(0);
        assertEquals("text", pipeline.stages().get(0).inputKey());
    }

    @Test
    void evaluatorExtractsInputByKeyNotByPosition() {
        StreamingStepFactory<String, String> factory =
            (params, svc) -> new StreamingStep<>() {
                @Override public Class<String> inputType() { return String.class; }
                @Override public Class<String> outputType() { return String.class; }
                @Override public String apply(String input) { return "got:" + input; }
            };

        // params have "model" before "text" in iteration order
        var params = new java.util.LinkedHashMap<String, Object>();
        params.put("model", "gpt-4");
        params.put("text", "hello");

        var stages = List.of(
            new ResolvedStep.FusedStageDescriptor("s1", "a",
                params, Map.of(), "text",
                (StreamingStepFactory<?, ?>) factory),
            new ResolvedStep.FusedStageDescriptor("s2", "b",
                Map.of(), Map.of(), "input",
                (StreamingStepFactory<?, ?>) factory));
        var fused = new ResolvedStep.FusedPipelineStep("s2", stages, Map.of());

        var evaluator = new StructuralStepEvaluator(
            new io.casehub.yaml.core.condition.ConditionEvaluator(null));
        Result result = evaluator.evaluate(fused,
            new io.casehub.yaml.core.resolver.VariableResolver(Map.of(), Set.of()),
            (step, resolver) -> Result.of(Map.of()));

        assertTrue(result.isSuccess());
        // Should extract "hello" (by key "text"), not "gpt-4" (first value)
        assertEquals("got:got:hello", result.output().get("output"));
    }

    // --- #13: Chain-breaking decorator on FIRST step ---

    @Test
    void chainBreakingDecoratorOnFirstStepPreventsChainStart() {
        var reg = registry(
            streamingDef("step1", String.class, String.class, "input", identityFactory()),
            streamingDef("step2", String.class, String.class, "input", identityFactory()));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("s1", "step1", Map.of(),
                Map.of("background", true)),
            new ResolvedStep.PluginStep("s2", "step2", Map.of(), Map.of()));

        List<ResolvedStep> fused = FusionPass.fuse(steps, reg);
        assertEquals(2, fused.size());
        assertInstanceOf(ResolvedStep.PluginStep.class, fused.get(0));
        assertInstanceOf(ResolvedStep.PluginStep.class, fused.get(1));
    }

    // --- #14: Variable references in nested map values ---

    @Test
    void variableReferencesInNestedMapsDetected() {
        var reg = registry(
            streamingDef("step1", String.class, String.class, "input", identityFactory()),
            streamingDef("step2", String.class, String.class, "input", identityFactory()));

        Map<String, Object> nestedParams = Map.of(
            "config", Map.of("ref", "${result.s1.output}"));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("s1", "step1", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("s2", "step2", nestedParams, Map.of()));

        // s1 is referenced in s2's nested params — s1 should not be fused away
        List<ResolvedStep> fused = FusionPass.fuse(steps, reg);
        assertEquals(2, fused.size(), "s1 is referenced in nested params — should not fuse");
    }

    // --- #15: Subclass type compatibility ---

    @Test
    void subclassTypeCompatibility() {
        var reg = registry(
            streamingDef("produce-bais", String.class, ByteArrayInputStream.class, "input",
                (StreamingStepFactory<?, ?>) (StreamingStepFactory<String, ByteArrayInputStream>)
                    (p, s) -> new StreamingStep<>() {
                        @Override public Class<String> inputType() { return String.class; }
                        @Override public Class<ByteArrayInputStream> outputType() { return ByteArrayInputStream.class; }
                        @Override public ByteArrayInputStream apply(String input) {
                            return new ByteArrayInputStream(input.getBytes());
                        }
                    }),
            streamingDef("consume-is", InputStream.class, String.class, "input",
                (StreamingStepFactory<?, ?>) (StreamingStepFactory<InputStream, String>)
                    (p, s) -> new StreamingStep<>() {
                        @Override public Class<InputStream> inputType() { return InputStream.class; }
                        @Override public Class<String> outputType() { return String.class; }
                        @Override public String apply(InputStream input) { return "consumed"; }
                    }));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("s1", "produce-bais", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("s2", "consume-is", Map.of(), Map.of()));

        List<ResolvedStep> fused = FusionPass.fuse(steps, reg);
        assertEquals(1, fused.size(), "ByteArrayInputStream is assignable to InputStream — should fuse");
        assertInstanceOf(ResolvedStep.FusedPipelineStep.class, fused.get(0));
    }

    // --- #16: Tighter assertion for variable reference test ---

    @Test
    void variableReferenceOnFirstStepBreaksFusionPrecise() {
        var reg = registry(
            streamingDef("step1", String.class, String.class, "input", identityFactory()),
            streamingDef("step2", String.class, String.class, "input", identityFactory()),
            streamingDef("step3", String.class, String.class, "input", identityFactory()));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("s1", "step1", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("s2", "step2", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("s3", "step3",
                Map.of("ref", "${result.s1.output}"), Map.of()));

        List<ResolvedStep> fused = FusionPass.fuse(steps, reg);
        // s1 is referenced — cannot be fused (it would lose its result)
        // Expected: s1 standalone, then s2 standalone (can't fuse with s3 alone? Actually s2+s3 can fuse if s2 is unreferenced)
        // Let's verify s1 is NOT inside any FusedPipelineStep
        for (ResolvedStep s : fused) {
            if (s instanceof ResolvedStep.FusedPipelineStep fps) {
                for (var stage : fps.stages()) {
                    assertNotEquals("s1", stage.stepName(),
                        "s1 must not be inside a fused chain because it's referenced");
                }
            }
        }
    }

    // --- #17: SpeedMultiplier on timeout and retry ---

    @Test
    void speedMultiplierAffectsTimeout() {
        java.util.function.Function<String, String> slow = s -> {
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return s;
        };
        // 100ms timeout at 0.1x speed = effectively 1000ms timeout — should pass
        var wrapped = InlineDecoratorWrappers.withTimeout(slow, java.time.Duration.ofMillis(100), () -> 0.1);
        assertEquals("hello", wrapped.apply("hello"));
    }

    @Test
    void speedMultiplierAffectsRetryDelay() {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        long start = System.nanoTime();
        var wrapped = InlineDecoratorWrappers.withRetry(
            (java.util.function.Function<String, String>) s -> {
                if (attempts.incrementAndGet() < 3) throw new RuntimeException("fail");
                return s;
            }, 3, java.time.Duration.ofMillis(200), "fixed", () -> 10.0);
        wrapped.apply("hello");
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        // 200ms delay at 10x speed = 20ms per retry, 2 retries = ~40ms total delay
        assertTrue(elapsed < 200, "10x speed should make 2x200ms retries complete in < 200ms, was " + elapsed);
    }

    // --- Helpers ---

    private static StreamingStepFactory<String, String> identityFactory() {
        return (p, s) -> new StreamingStep<>() {
            @Override public Class<String> inputType() { return String.class; }
            @Override public Class<String> outputType() { return String.class; }
            @Override public String apply(String input) { return input; }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Definition streamingDef(String name, Class<?> in, Class<?> out,
                                            String inputKey, StreamingStepFactory<?, ?> factory) {
        return new Definition(name, name, Map.of(), Map.of(), Portability.JAVA,
            (p, s) -> Result.of(Map.of()), "steps",
            Optional.of(new StreamingTypeInfo(in, out, inputKey, (StreamingStepFactory) factory)));
    }

    private static TestRegistry registry(Definition... defs) {
        var r = new TestRegistry();
        for (Definition d : defs) r.register(d);
        return r;
    }

    private static class TestRegistry implements PluginRegistry {
        private final Map<String, Definition> defs = new HashMap<>();
        @Override public void register(Definition d) { defs.put(d.name(), d); }
        @Override public Optional<Definition> resolve(String name) { return Optional.ofNullable(defs.get(name)); }
        @Override public Set<String> availableActions() { return defs.keySet(); }
    }
}
