package io.casehub.yaml.core.step;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StepParameterTypeTest {

    @ParameterizedTest
    @CsvSource({"STRING,STRING", "string,STRING", "INTEGER,INTEGER", "NUMBER,NUMBER",
                "DECIMAL,NUMBER", "BOOLEAN,BOOLEAN", "ARRAY,ARRAY", "OBJECT,OBJECT"})
    void fromStringParsesAllTypes(String input, StepParameterType expected) {
        assertThat(StepParameterType.fromString(input)).isEqualTo(expected);
    }

    @Test
    void fromStringRejectsUnknown() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> StepParameterType.fromString("map"))
                .withMessageContaining("Unknown step parameter type");
    }

    @Test
    void isScalarTrueForScalarTypes() {
        assertThat(StepParameterType.STRING.isScalar()).isTrue();
        assertThat(StepParameterType.INTEGER.isScalar()).isTrue();
        assertThat(StepParameterType.NUMBER.isScalar()).isTrue();
        assertThat(StepParameterType.BOOLEAN.isScalar()).isTrue();
    }

    @Test
    void isScalarFalseForComplexTypes() {
        assertThat(StepParameterType.ARRAY.isScalar()).isFalse();
        assertThat(StepParameterType.OBJECT.isScalar()).isFalse();
    }

    @Test
    void validateChecksRuntimeTypes() {
        assertThat(StepParameterType.STRING.validate("hello")).isTrue();
        assertThat(StepParameterType.STRING.validate(42)).isFalse();
        assertThat(StepParameterType.INTEGER.validate(42)).isTrue();
        assertThat(StepParameterType.INTEGER.validate(42L)).isTrue();
        assertThat(StepParameterType.INTEGER.validate(3.14)).isFalse();
        assertThat(StepParameterType.NUMBER.validate(3.14)).isTrue();
        assertThat(StepParameterType.NUMBER.validate(42)).isTrue();
        assertThat(StepParameterType.BOOLEAN.validate(true)).isTrue();
        assertThat(StepParameterType.BOOLEAN.validate("true")).isFalse();
        assertThat(StepParameterType.ARRAY.validate(List.of("a", "b"))).isTrue();
        assertThat(StepParameterType.ARRAY.validate("not a list")).isFalse();
        assertThat(StepParameterType.OBJECT.validate(Map.of("k", "v"))).isTrue();
        assertThat(StepParameterType.OBJECT.validate("not a map")).isFalse();
    }

    @Test
    void parseScalarParsesStrings() {
        assertThat(StepParameterType.STRING.parseScalar("hello")).isEqualTo("hello");
        assertThat(StepParameterType.INTEGER.parseScalar("42")).isEqualTo(42);
        assertThat(StepParameterType.NUMBER.parseScalar("3.14")).isEqualTo(3.14);
        assertThat(StepParameterType.BOOLEAN.parseScalar("true")).isEqualTo(true);
    }

    @Test
    void parseScalarRejectsComplexTypes() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> StepParameterType.ARRAY.parseScalar("[]"))
                .withMessageContaining("Cannot parse");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> StepParameterType.OBJECT.parseScalar("{}"))
                .withMessageContaining("Cannot parse");
    }
}
