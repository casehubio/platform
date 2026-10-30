package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.plugin.api.StreamingStep;
import io.casehub.yaml.plugin.api.StreamingStepFactory;
import io.casehub.yaml.step.catalog.ResolvedStep;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class FusedChainEvaluationTest {

    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator(null);

    @Test
    void evaluatesTypedTransformChain() {
        StreamingStepFactory<String, Integer> factory1 =
            (p, s) -> new StreamingStep<>() {
                @Override public Class<String> inputType() { return String.class; }
                @Override public Class<Integer> outputType() { return Integer.class; }
                @Override public Integer apply(String input) { return input.length(); }
            };
        StreamingStepFactory<Integer, String> factory2 =
            (p, s) -> new StreamingStep<>() {
                @Override public Class<Integer> inputType() { return Integer.class; }
                @Override public Class<String> outputType() { return String.class; }
                @Override public String apply(Integer input) { return "length=" + input; }
            };

        var stages = List.of(
            new ResolvedStep.FusedStageDescriptor("measure", "measure",
                Map.of("input", "hello"), Map.of(), "input",
                (StreamingStepFactory<?, ?>) factory1),
            new ResolvedStep.FusedStageDescriptor("format", "format",
                Map.of(), Map.of(), "input",
                (StreamingStepFactory<?, ?>) factory2));
        var fused = new ResolvedStep.FusedPipelineStep("format", stages, Map.of());

        var evaluator = new StructuralStepEvaluator(conditionEvaluator);
        Result result = evaluator.evaluate(fused,
            new io.casehub.yaml.core.resolver.VariableResolver(Map.of(), java.util.Set.of()),
            (step, resolver) -> Result.of(Map.of()));

        assertTrue(result.isSuccess());
        assertEquals("length=5", result.output().get(StreamingStep.OUTPUT_KEY));
    }

    @Test
    void chainFailureProducesResultFailure() {
        StreamingStepFactory<String, String> factory1 =
            (p, s) -> new StreamingStep<>() {
                @Override public Class<String> inputType() { return String.class; }
                @Override public Class<String> outputType() { return String.class; }
                @Override public String apply(String input) { return input.toUpperCase(); }
            };
        StreamingStepFactory<String, String> factory2 =
            (p, s) -> new StreamingStep<>() {
                @Override public Class<String> inputType() { return String.class; }
                @Override public Class<String> outputType() { return String.class; }
                @Override public String apply(String input) {
                    throw new RuntimeException("processing failed");
                }
            };

        var stages = List.of(
            new ResolvedStep.FusedStageDescriptor("step1", "step1",
                Map.of("input", "hello"), Map.of(), "input",
                (StreamingStepFactory<?, ?>) factory1),
            new ResolvedStep.FusedStageDescriptor("step2", "step2",
                Map.of(), Map.of(), "input",
                (StreamingStepFactory<?, ?>) factory2));
        var fused = new ResolvedStep.FusedPipelineStep("step2", stages, Map.of());

        var evaluator = new StructuralStepEvaluator(conditionEvaluator);
        Result result = evaluator.evaluate(fused,
            new io.casehub.yaml.core.resolver.VariableResolver(Map.of(), java.util.Set.of()),
            (step, resolver) -> Result.of(Map.of()));

        assertFalse(result.isSuccess());
        assertTrue(result instanceof Result.Failure f && f.message().contains("step2"));
    }

    @Test
    void inputStreamClosedOnMidChainFailure() {
        var closed = new AtomicBoolean(false);
        StreamingStepFactory<String, InputStream> factory1 =
            (p, s) -> new StreamingStep<>() {
                @Override public Class<String> inputType() { return String.class; }
                @Override public Class<InputStream> outputType() { return InputStream.class; }
                @Override public InputStream apply(String input) {
                    return new ByteArrayInputStream(input.getBytes()) {
                        @Override public void close() throws java.io.IOException {
                            closed.set(true);
                            super.close();
                        }
                    };
                }
            };
        StreamingStepFactory<InputStream, String> factory2 =
            (p, s) -> new StreamingStep<>() {
                @Override public Class<InputStream> inputType() { return InputStream.class; }
                @Override public Class<String> outputType() { return String.class; }
                @Override public String apply(InputStream input) {
                    throw new RuntimeException("transcription failed");
                }
            };

        var stages = List.of(
            new ResolvedStep.FusedStageDescriptor("download", "download",
                Map.of("url", "http://example.com"), Map.of(), "url",
                (StreamingStepFactory<?, ?>) factory1),
            new ResolvedStep.FusedStageDescriptor("transcribe", "transcribe",
                Map.of(), Map.of(), "input",
                (StreamingStepFactory<?, ?>) factory2));
        var fused = new ResolvedStep.FusedPipelineStep("transcribe", stages, Map.of());

        var evaluator = new StructuralStepEvaluator(conditionEvaluator);
        Result result = evaluator.evaluate(fused,
            new io.casehub.yaml.core.resolver.VariableResolver(Map.of(), java.util.Set.of()),
            (step, resolver) -> Result.of(Map.of()));

        assertFalse(result.isSuccess());
        assertTrue(closed.get(), "InputStream should be closed on chain failure");
    }

    @Test
    void threeStepByteStreamChain() {
        StreamingStepFactory<String, InputStream> download =
            (p, s) -> new StreamingStep<>() {
                @Override public Class<String> inputType() { return String.class; }
                @Override public Class<InputStream> outputType() { return InputStream.class; }
                @Override public InputStream apply(String url) {
                    return new ByteArrayInputStream(("downloaded:" + url).getBytes());
                }
            };
        StreamingStepFactory<InputStream, InputStream> transform =
            (p, s) -> new StreamingStep<>() {
                @Override public Class<InputStream> inputType() { return InputStream.class; }
                @Override public Class<InputStream> outputType() { return InputStream.class; }
                @Override public InputStream apply(InputStream in) {
                    try {
                        String data = new String(in.readAllBytes());
                        return new ByteArrayInputStream(data.toUpperCase().getBytes());
                    } catch (Exception e) { throw new RuntimeException(e); }
                }
            };
        StreamingStepFactory<InputStream, String> transcribe =
            (p, s) -> new StreamingStep<>() {
                @Override public Class<InputStream> inputType() { return InputStream.class; }
                @Override public Class<String> outputType() { return String.class; }
                @Override public String apply(InputStream in) {
                    try { return "transcribed:" + new String(in.readAllBytes()); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }
            };

        var stages = List.of(
            new ResolvedStep.FusedStageDescriptor("download", "download",
                Map.of("url", "http://example.com/audio.wav"), Map.of(), "url",
                (StreamingStepFactory<?, ?>) download),
            new ResolvedStep.FusedStageDescriptor("transform", "transform",
                Map.of(), Map.of(), "input",
                (StreamingStepFactory<?, ?>) transform),
            new ResolvedStep.FusedStageDescriptor("transcribe", "transcribe",
                Map.of(), Map.of(), "input",
                (StreamingStepFactory<?, ?>) transcribe));
        var fused = new ResolvedStep.FusedPipelineStep("transcribe", stages, Map.of());

        var evaluator = new StructuralStepEvaluator(conditionEvaluator);
        Result result = evaluator.evaluate(fused,
            new io.casehub.yaml.core.resolver.VariableResolver(Map.of(), java.util.Set.of()),
            (step, resolver) -> Result.of(Map.of()));

        assertTrue(result.isSuccess());
        String output = (String) result.output().get(StreamingStep.OUTPUT_KEY);
        assertEquals("transcribed:DOWNLOADED:HTTP://EXAMPLE.COM/AUDIO.WAV", output);
    }
}
