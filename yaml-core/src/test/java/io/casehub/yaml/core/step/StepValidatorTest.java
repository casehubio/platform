package io.casehub.yaml.core.step;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StepValidatorTest {

    private StepDefinition defWithRequiredString() {
        return new StepDefinition("test", null,
                Map.of("name", new StepParameter(StepParameterType.STRING, true, null, null, null, null)),
                Map.of("result", new StepParameter(StepParameterType.STRING, true, null, null, null, null)),
                new InvokeBinding.Mcp("test"));
    }

    @Test
    void validInputsPasses() {
        var errors = StepValidator.validateStep("test", Map.of("name", "hello"), defWithRequiredString());
        assertThat(errors).isEmpty();
    }

    @Test
    void missingRequiredInputFails() {
        var errors = StepValidator.validateStep("test", Map.of(), defWithRequiredString());
        assertThat(errors).anyMatch(e -> e.contains("name") && e.contains("required"));
    }

    @Test
    void wrongTypeFails() {
        var def = new StepDefinition("test", null,
                Map.of("count", new StepParameter(StepParameterType.INTEGER, true, null, null, null, null)),
                Map.of(), new InvokeBinding.Mcp("test"));

        var errors = StepValidator.validateStep("test", Map.of("count", "not-a-number"), def);
        assertThat(errors).anyMatch(e -> e.contains("count") && e.contains("INTEGER"));
    }

    @Test
    void invalidEnumValueFails() {
        var def = new StepDefinition("test", null,
                Map.of("level", new StepParameter(StepParameterType.STRING, true, null,
                        List.of("LOW", "HIGH"), null, null)),
                Map.of(), new InvokeBinding.Mcp("test"));

        var errors = StepValidator.validateStep("test", Map.of("level", "INVALID"), def);
        assertThat(errors).anyMatch(e -> e.contains("level") && e.contains("allowedValues"));
    }

    @Test
    void validEnumValuePasses() {
        var def = new StepDefinition("test", null,
                Map.of("level", new StepParameter(StepParameterType.STRING, true, null,
                        List.of("LOW", "HIGH"), null, null)),
                Map.of(), new InvokeBinding.Mcp("test"));

        var errors = StepValidator.validateStep("test", Map.of("level", "LOW"), def);
        assertThat(errors).isEmpty();
    }

    @Test
    void validOutputsPasses() {
        var errors = StepValidator.validateOutputs(Map.of("result", "ok"), defWithRequiredString());
        assertThat(errors).isEmpty();
    }

    @Test
    void missingRequiredOutputFails() {
        var errors = StepValidator.validateOutputs(Map.of(), defWithRequiredString());
        assertThat(errors).anyMatch(e -> e.contains("result") && e.contains("required"));
    }

    @Test
    void dateFormatValidation() {
        var def = new StepDefinition("test", null,
                Map.of("date", new StepParameter(StepParameterType.STRING, true, null, null, "date", null)),
                Map.of(), new InvokeBinding.Mcp("test"));

        var valid = StepValidator.validateStep("test", Map.of("date", "2026-01-15"), def);
        assertThat(valid).isEmpty();

        var invalid = StepValidator.validateStep("test", Map.of("date", "not-a-date"), def);
        assertThat(invalid).anyMatch(e -> e.contains("date") && e.contains("format"));
    }

    @Test
    void dateTimeFormatValidation() {
        var def = new StepDefinition("test", null,
                Map.of("ts", new StepParameter(StepParameterType.STRING, true, null, null, "date-time", null)),
                Map.of(), new InvokeBinding.Mcp("test"));

        var valid = StepValidator.validateStep("test", Map.of("ts", "2026-01-15T10:30:00+01:00"), def);
        assertThat(valid).isEmpty();

        var invalid = StepValidator.validateStep("test", Map.of("ts", "not-a-datetime"), def);
        assertThat(invalid).anyMatch(e -> e.contains("ts") && e.contains("format"));
    }

    @Test
    void uriFormatValidation() {
        var def = new StepDefinition("test", null,
                Map.of("url", new StepParameter(StepParameterType.STRING, true, null, null, "uri", null)),
                Map.of(), new InvokeBinding.Mcp("test"));

        var valid = StepValidator.validateStep("test", Map.of("url", "https://example.com/api"), def);
        assertThat(valid).isEmpty();
    }

    @Test
    void unknownFormatIsIgnored() {
        var def = new StepDefinition("test", null,
                Map.of("field", new StepParameter(StepParameterType.STRING, true, null, null, "custom-format", null)),
                Map.of(), new InvokeBinding.Mcp("test"));

        var errors = StepValidator.validateStep("test", Map.of("field", "anything"), def);
        assertThat(errors).isEmpty();
    }

    @Test
    void optionalInputCanBeAbsent() {
        var def = new StepDefinition("test", null,
                Map.of("optional", new StepParameter(StepParameterType.STRING, false, null, null, null, null)),
                Map.of(), new InvokeBinding.Mcp("test"));

        var errors = StepValidator.validateStep("test", Map.of(), def);
        assertThat(errors).isEmpty();
    }

    @Test
    void arrayTypeValidation() {
        var def = new StepDefinition("test", null,
                Map.of("items", new StepParameter(StepParameterType.ARRAY, true, null, null, null, null)),
                Map.of(), new InvokeBinding.Mcp("test"));

        var valid = StepValidator.validateStep("test", Map.of("items", List.of("a", "b")), def);
        assertThat(valid).isEmpty();

        var invalid = StepValidator.validateStep("test", Map.of("items", "not-a-list"), def);
        assertThat(invalid).anyMatch(e -> e.contains("items") && e.contains("ARRAY"));
    }

    @Test
    void objectTypeValidation() {
        var def = new StepDefinition("test", null,
                Map.of("data", new StepParameter(StepParameterType.OBJECT, true, null, null, null, null)),
                Map.of(), new InvokeBinding.Mcp("test"));

        var valid = StepValidator.validateStep("test", Map.of("data", Map.of("k", "v")), def);
        assertThat(valid).isEmpty();

        var invalid = StepValidator.validateStep("test", Map.of("data", "not-a-map"), def);
        assertThat(invalid).anyMatch(e -> e.contains("data") && e.contains("OBJECT"));
    }

    @Test
    void multipleErrorsCollected() {
        var def = new StepDefinition("test", null,
                Map.of(
                        "a", new StepParameter(StepParameterType.STRING, true, null, null, null, null),
                        "b", new StepParameter(StepParameterType.INTEGER, true, null, null, null, null)),
                Map.of(), new InvokeBinding.Mcp("test"));

        var errors = StepValidator.validateStep("test", Map.of(), def);
        assertThat(errors).hasSize(2);
    }

    @Test
    void formatOnlyCheckedForStringType() {
        var def = new StepDefinition("test", null,
                Map.of("count", new StepParameter(StepParameterType.INTEGER, true, null, null, "date", null)),
                Map.of(), new InvokeBinding.Mcp("test"));

        var errors = StepValidator.validateStep("test", Map.of("count", 42), def);
        assertThat(errors).isEmpty();
    }
}
