package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
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
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class StreamingPipelineIntegrationTest {

    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator(null);

    // --- Byte stream pipeline steps ---

    static StreamingStepFactory<String, InputStream> downloadFactory() {
        return (params, svc) -> new StreamingStep<>() {
            @Override public Class<String> inputType() { return String.class; }
            @Override public Class<InputStream> outputType() { return InputStream.class; }
            @Override public InputStream apply(String url) {
                return new ByteArrayInputStream(("downloaded:" + url).getBytes());
            }
        };
    }

    static StreamingStepFactory<InputStream, InputStream> transformFactory() {
        return (params, svc) -> new StreamingStep<>() {
            @Override public Class<InputStream> inputType() { return InputStream.class; }
            @Override public Class<InputStream> outputType() { return InputStream.class; }
            @Override public InputStream apply(InputStream in) {
                try { return new ByteArrayInputStream(new String(in.readAllBytes()).toUpperCase().getBytes()); }
                catch (IOException e) { throw new RuntimeException(e); }
            }
        };
    }

    static StreamingStepFactory<InputStream, String> transcribeFactory() {
        return (params, svc) -> new StreamingStep<>() {
            @Override public Class<InputStream> inputType() { return InputStream.class; }
            @Override public Class<String> outputType() { return String.class; }
            @Override public String apply(InputStream in) {
                try { return "transcribed:" + new String(in.readAllBytes()); }
                catch (IOException e) { throw new RuntimeException(e); }
            }
        };
    }

    // --- Typed transform pipeline steps ---

    record SensorEvent(String sensorId, double temperature) {}
    record AnnotatedEvent(SensorEvent event, boolean anomaly) {}
    record AlertPayload(String message, String severity) {}

    static StreamingStepFactory<String, SensorEvent> ingestFactory() {
        return (params, svc) -> new StreamingStep<>() {
            @Override public Class<String> inputType() { return String.class; }
            @Override public Class<SensorEvent> outputType() { return SensorEvent.class; }
            @Override public SensorEvent apply(String raw) {
                return new SensorEvent("s1", Double.parseDouble(raw));
            }
        };
    }

    static StreamingStepFactory<SensorEvent, AnnotatedEvent> detectFactory() {
        return (params, svc) -> new StreamingStep<>() {
            @Override public Class<SensorEvent> inputType() { return SensorEvent.class; }
            @Override public Class<AnnotatedEvent> outputType() { return AnnotatedEvent.class; }
            @Override public AnnotatedEvent apply(SensorEvent event) {
                double threshold = params.containsKey("threshold")
                    ? ((Number) params.get("threshold")).doubleValue() : 2.5;
                return new AnnotatedEvent(event, event.temperature() > threshold);
            }
        };
    }

    static StreamingStepFactory<AnnotatedEvent, AlertPayload> formatFactory() {
        return (params, svc) -> new StreamingStep<>() {
            @Override public Class<AnnotatedEvent> inputType() { return AnnotatedEvent.class; }
            @Override public Class<AlertPayload> outputType() { return AlertPayload.class; }
            @Override public AlertPayload apply(AnnotatedEvent event) {
                return new AlertPayload("Temp: " + event.event().temperature(),
                    event.anomaly() ? "HIGH" : "LOW");
            }
        };
    }

    // --- Tests ---

    @Test
    void byteStreamPipelineFusesAndExecutes() {
        var registry = createRegistry(
            streamingDef("download", String.class, InputStream.class, "url", downloadFactory()),
            streamingDef("transform", InputStream.class, InputStream.class, "input", transformFactory()),
            streamingDef("transcribe", InputStream.class, String.class, "input", transcribeFactory()));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("download", "download",
                Map.of("url", "http://example.com/audio"), Map.of()),
            new ResolvedStep.PluginStep("transform", "transform", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("transcribe", "transcribe", Map.of(), Map.of()));

        List<ResolvedStep> fused = FusionPass.fuse(steps, registry);
        assertEquals(1, fused.size(), "3 streaming steps should fuse into 1");
        assertInstanceOf(ResolvedStep.FusedPipelineStep.class, fused.get(0));

        var evaluator = new StructuralStepEvaluator(conditionEvaluator);
        Result result = evaluator.evaluate(fused.get(0),
            new io.casehub.yaml.core.resolver.VariableResolver(Map.of(), Set.of()),
            (step, resolver) -> Result.of(Map.of()));

        assertTrue(result.isSuccess());
        assertEquals("transcribed:DOWNLOADED:HTTP://EXAMPLE.COM/AUDIO",
            result.output().get(StreamingStep.OUTPUT_KEY));
    }

    @Test
    void typedTransformPipelineFusesAndExecutes() {
        var registry = createRegistry(
            streamingDef("ingest", String.class, SensorEvent.class, "raw-payload", ingestFactory()),
            streamingDef("detect", SensorEvent.class, AnnotatedEvent.class, "event", detectFactory()),
            streamingDef("format", AnnotatedEvent.class, AlertPayload.class, "event", formatFactory()));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("ingest", "ingest",
                Map.of("raw-payload", "42.5"), Map.of()),
            new ResolvedStep.PluginStep("detect", "detect",
                Map.of("threshold", 40.0), Map.of()),
            new ResolvedStep.PluginStep("format", "format", Map.of(), Map.of()));

        List<ResolvedStep> fused = FusionPass.fuse(steps, registry);
        assertEquals(1, fused.size(), "3 typed transform steps should fuse into 1");

        var evaluator = new StructuralStepEvaluator(conditionEvaluator);
        Result result = evaluator.evaluate(fused.get(0),
            new io.casehub.yaml.core.resolver.VariableResolver(Map.of(), Set.of()),
            (step, resolver) -> Result.of(Map.of()));

        assertTrue(result.isSuccess());
        var payload = (AlertPayload) result.output().get(StreamingStep.OUTPUT_KEY);
        assertEquals("HIGH", payload.severity());
        assertTrue(payload.message().contains("42.5"));
    }

    @Test
    void mixedPipelineOnlyFusesStreamingSteps() {
        var nonStreamingDef = new Definition("cleanup", "cleanup",
            Map.of(), Map.of(), Portability.JAVA,
            (p, s) -> Result.of(Map.of("cleaned", "yes")), "steps");
        var registry = createRegistry(
            streamingDef("download", String.class, InputStream.class, "url", downloadFactory()),
            streamingDef("transcribe", InputStream.class, String.class, "input", transcribeFactory()));
        registry.register(nonStreamingDef);

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("download", "download",
                Map.of("url", "http://example.com"), Map.of()),
            new ResolvedStep.PluginStep("transcribe", "transcribe", Map.of(), Map.of()),
            new ResolvedStep.PluginStep("cleanup", "cleanup", Map.of(), Map.of()));

        List<ResolvedStep> fused = FusionPass.fuse(steps, registry);
        assertEquals(2, fused.size(), "2 streaming + 1 non-streaming = fused + standalone");
        assertInstanceOf(ResolvedStep.FusedPipelineStep.class, fused.get(0));
        assertInstanceOf(ResolvedStep.PluginStep.class, fused.get(1));
    }

    @Test
    void resourceCleanupOnMidChainFailure() {
        var closed = new AtomicBoolean(false);
        StreamingStepFactory<String, InputStream> leakyDownload = (params, svc) -> new StreamingStep<>() {
            @Override public Class<String> inputType() { return String.class; }
            @Override public Class<InputStream> outputType() { return InputStream.class; }
            @Override public InputStream apply(String url) {
                return new ByteArrayInputStream("data".getBytes()) {
                    @Override public void close() throws IOException {
                        closed.set(true);
                        super.close();
                    }
                };
            }
        };
        StreamingStepFactory<InputStream, String> failingTranscribe = (params, svc) -> new StreamingStep<>() {
            @Override public Class<InputStream> inputType() { return InputStream.class; }
            @Override public Class<String> outputType() { return String.class; }
            @Override public String apply(InputStream in) {
                throw new RuntimeException("STT service down");
            }
        };

        var registry = createRegistry(
            streamingDef("download", String.class, InputStream.class, "url", leakyDownload),
            streamingDef("transcribe", InputStream.class, String.class, "input", failingTranscribe));

        List<ResolvedStep> steps = List.of(
            new ResolvedStep.PluginStep("download", "download",
                Map.of("url", "http://example.com"), Map.of()),
            new ResolvedStep.PluginStep("transcribe", "transcribe", Map.of(), Map.of()));

        List<ResolvedStep> fused = FusionPass.fuse(steps, registry);
        assertEquals(1, fused.size());

        var evaluator = new StructuralStepEvaluator(conditionEvaluator);
        Result result = evaluator.evaluate(fused.get(0),
            new io.casehub.yaml.core.resolver.VariableResolver(Map.of(), Set.of()),
            (step, resolver) -> Result.of(Map.of()));

        assertFalse(result.isSuccess());
        assertTrue(closed.get(), "InputStream must be closed on chain failure");
    }

    // --- Helpers ---

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Definition streamingDef(String name, Class<?> in, Class<?> out,
                                            String inputKey, StreamingStepFactory<?, ?> factory) {
        return new Definition(name, name, Map.of(), Map.of(), Portability.JAVA,
            (p, s) -> Result.of(Map.of()), "steps",
            Optional.of(new StreamingTypeInfo(in, out, inputKey, (StreamingStepFactory) factory)));
    }

    private static TestRegistry createRegistry(Definition... defs) {
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
