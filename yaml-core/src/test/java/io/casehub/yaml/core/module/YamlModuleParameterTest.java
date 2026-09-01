package io.casehub.yaml.core.module;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlModuleParameterTest {

    // --- Builder basics ---

    @Test
    void builder_defaults() {
        var param = YamlModuleParameter.builder().build();
        assertThat(param.type()).isEqualTo(ParameterType.STRING);
        assertThat(param.required()).isFalse();
        assertThat(param.defaultValue()).isNull();
        assertThat(param.minLength()).isNull();
        assertThat(param.maxLength()).isNull();
        assertThat(param.pattern()).isNull();
        assertThat(param.minimum()).isNull();
        assertThat(param.maximum()).isNull();
        assertThat(param.allowedValues()).isEmpty();
        assertThat(param.constraintDescription()).isNull();
    }

    @Test
    void builder_sets_all_fields() {
        var param = YamlModuleParameter.builder()
                .type(ParameterType.INTEGER)
                .required()
                .defaultValue("42")
                .minimum(1)
                .maximum(100)
                .constraintDescription("Must be 1-100")
                .build();
        assertThat(param.type()).isEqualTo(ParameterType.INTEGER);
        assertThat(param.required()).isTrue();
        assertThat(param.defaultValue()).isEqualTo("42");
        assertThat(param.minimum()).isEqualTo(1);
        assertThat(param.maximum()).isEqualTo(100);
        assertThat(param.constraintDescription()).isEqualTo("Must be 1-100");
    }

    @Test
    void builder_required_with_boolean() {
        var param = YamlModuleParameter.builder().required(false).build();
        assertThat(param.required()).isFalse();
    }

    @Test
    void builder_allowedValues_varargs() {
        var param = YamlModuleParameter.builder()
                .allowedValues("us-east", "eu-west", "ap-south")
                .build();
        assertThat(param.allowedValues()).containsExactly("us-east", "eu-west", "ap-south");
    }

    @Test
    void builder_allowedValues_list() {
        var param = YamlModuleParameter.builder()
                .allowedValues(List.of("a", "b"))
                .build();
        assertThat(param.allowedValues()).containsExactly("a", "b");
    }

    @Test
    void builder_string_with_length_constraints() {
        var param = YamlModuleParameter.builder()
                .type(ParameterType.STRING)
                .minLength(3)
                .maxLength(50)
                .pattern("^[a-z]+$")
                .build();
        assertThat(param.minLength()).isEqualTo(3);
        assertThat(param.maxLength()).isEqualTo(50);
        assertThat(param.pattern()).isEqualTo("^[a-z]+$");
    }

    @Test
    void builder_list_with_length_constraints() {
        var param = YamlModuleParameter.builder()
                .type(ParameterType.LIST)
                .minLength(1)
                .maxLength(10)
                .build();
        assertThat(param.type()).isEqualTo(ParameterType.LIST);
        assertThat(param.minLength()).isEqualTo(1);
    }

    // --- Constraint/type coherence ---

    @Test
    void coherence_minimum_on_string_throws() {
        assertThatThrownBy(() -> YamlModuleParameter.builder()
                .type(ParameterType.STRING)
                .minimum(5)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum/maximum")
                .hasMessageContaining("STRING")
                .hasMessageContaining("minLength/maxLength");
    }

    @Test
    void coherence_maximum_on_boolean_throws() {
        assertThatThrownBy(() -> YamlModuleParameter.builder()
                .type(ParameterType.BOOLEAN)
                .maximum(1)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum/maximum")
                .hasMessageContaining("BOOLEAN");
    }

    @Test
    void coherence_maximum_on_list_throws() {
        assertThatThrownBy(() -> YamlModuleParameter.builder()
                .type(ParameterType.LIST)
                .maximum(10)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum/maximum")
                .hasMessageContaining("LIST");
    }

    @Test
    void coherence_minLength_on_integer_throws() {
        assertThatThrownBy(() -> YamlModuleParameter.builder()
                .type(ParameterType.INTEGER)
                .minLength(5)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minLength/maxLength")
                .hasMessageContaining("INTEGER")
                .hasMessageContaining("minimum/maximum");
    }

    @Test
    void coherence_maxLength_on_number_throws() {
        assertThatThrownBy(() -> YamlModuleParameter.builder()
                .type(ParameterType.NUMBER)
                .maxLength(10)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minLength/maxLength")
                .hasMessageContaining("NUMBER");
    }

    @Test
    void coherence_pattern_on_integer_throws() {
        assertThatThrownBy(() -> YamlModuleParameter.builder()
                .type(ParameterType.INTEGER)
                .pattern("^\\d+$")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pattern")
                .hasMessageContaining("INTEGER");
    }

    @Test
    void coherence_pattern_on_boolean_throws() {
        assertThatThrownBy(() -> YamlModuleParameter.builder()
                .type(ParameterType.BOOLEAN)
                .pattern("true|false")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pattern")
                .hasMessageContaining("BOOLEAN");
    }

    @Test
    void coherence_minimum_on_integer_passes() {
        var param = YamlModuleParameter.builder()
                .type(ParameterType.INTEGER)
                .minimum(0)
                .maximum(65535)
                .build();
        assertThat(param.minimum()).isEqualTo(0);
        assertThat(param.maximum()).isEqualTo(65535);
    }

    @Test
    void coherence_minimum_on_number_passes() {
        var param = YamlModuleParameter.builder()
                .type(ParameterType.NUMBER)
                .minimum(0.0)
                .maximum(1.0)
                .build();
        assertThat(param.minimum()).isEqualTo(0.0);
    }

    @Test
    void coherence_pattern_on_list_passes() {
        var param = YamlModuleParameter.builder()
                .type(ParameterType.LIST)
                .pattern("^[a-z]+$")
                .build();
        assertThat(param.pattern()).isEqualTo("^[a-z]+$");
    }
}
