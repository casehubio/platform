package io.casehub.yaml.core.step;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StepParameterTest {

    @Test
    void defaultsToStringType() {
        var param = new StepParameter(null, false, null, null, null, null);
        assertThat(param.type()).isEqualTo(StepParameterType.STRING);
        assertThat(param.allowedValues()).isEmpty();
    }

    @Test
    void rejectsDefaultValueOnComplexType() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StepParameter(
                        StepParameterType.OBJECT, false, "{}", null, null, null))
                .withMessageContaining("Default values are only supported for scalar types");
    }

    @Test
    void rejectsDefaultValueOnArrayType() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StepParameter(
                        StepParameterType.ARRAY, false, "[]", null, null, null))
                .withMessageContaining("Default values are only supported for scalar types");
    }

    @Test
    void acceptsDefaultValueOnScalarType() {
        var param = new StepParameter(StepParameterType.INTEGER, false, "42", null, null, null);
        assertThat(param.defaultValue()).isEqualTo("42");
    }

    @Test
    void allowedValuesArePreserved() {
        var param = new StepParameter(StepParameterType.STRING, true, null,
                List.of("LOW", "MEDIUM", "HIGH"), null, "Severity level");
        assertThat(param.required()).isTrue();
        assertThat(param.allowedValues()).containsExactly("LOW", "MEDIUM", "HIGH");
        assertThat(param.description()).isEqualTo("Severity level");
    }

    @Test
    void formatIsPreserved() {
        var param = new StepParameter(StepParameterType.STRING, false, null, null, "date", null);
        assertThat(param.format()).isEqualTo("date");
    }
}
