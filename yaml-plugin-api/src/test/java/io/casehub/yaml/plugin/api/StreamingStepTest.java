package io.casehub.yaml.plugin.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StreamingStepTest {

    @Test
    void typeTokensReflectGenericParameters() {
        StreamingStep<String, InputStream> step = new StreamingStep<>() {
            @Override public Class<String> inputType() { return String.class; }
            @Override public Class<InputStream> outputType() { return InputStream.class; }
            @Override public InputStream apply(String input) {
                return new ByteArrayInputStream(input.getBytes());
            }
        };
        assertEquals(String.class, step.inputType());
        assertEquals(InputStream.class, step.outputType());
    }

    @Test
    void applyTransformsInput() {
        StreamingStep<String, Integer> step = new StreamingStep<>() {
            @Override public Class<String> inputType() { return String.class; }
            @Override public Class<Integer> outputType() { return Integer.class; }
            @Override public Integer apply(String input) { return input.length(); }
        };
        assertEquals(5, step.apply("hello"));
    }

    @Test
    void materializeDefaultThrowsForHandWrittenImpls() {
        StreamingStep<String, String> step = new StreamingStep<>() {
            @Override public Class<String> inputType() { return String.class; }
            @Override public Class<String> outputType() { return String.class; }
            @Override public String apply(String input) { return input.toUpperCase(); }
        };
        assertThrows(UnsupportedOperationException.class,
            () -> step.materialize(Map.of(), new MapServiceRegistry()));
    }

    @Test
    void outputKeyConstantIsOutput() {
        assertEquals("output", StreamingStep.OUTPUT_KEY);
    }

    @Test
    void typeCompatibilityCheckViaIsAssignableFrom() {
        StreamingStep<InputStream, InputStream> step1 = new StreamingStep<>() {
            @Override public Class<InputStream> inputType() { return InputStream.class; }
            @Override public Class<InputStream> outputType() { return InputStream.class; }
            @Override public InputStream apply(InputStream in) { return in; }
        };
        StreamingStep<InputStream, String> step2 = new StreamingStep<>() {
            @Override public Class<InputStream> inputType() { return InputStream.class; }
            @Override public Class<String> outputType() { return String.class; }
            @Override public String apply(InputStream in) { return "transcribed"; }
        };
        assertTrue(step2.inputType().isAssignableFrom(step1.outputType()),
            "step2 should accept step1's output type");
    }

    @Test
    void materializeOverrideWorksForStandaloneExecution() {
        StreamingStep<String, String> step = new StreamingStep<>() {
            @Override public Class<String> inputType() { return String.class; }
            @Override public Class<String> outputType() { return String.class; }
            @Override public String apply(String input) { return input.toUpperCase(); }
            @Override public Result materialize(Map<String, Object> params, ServiceRegistry services) {
                String input = (String) params.get("text");
                String output = apply(input);
                return Result.of(Map.of(OUTPUT_KEY, output));
            }
        };
        Result result = step.materialize(Map.of("text", "hello"), new MapServiceRegistry());
        assertTrue(result.isSuccess());
        assertEquals("HELLO", result.output().get(StreamingStep.OUTPUT_KEY));
    }
}
