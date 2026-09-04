package io.casehub.yaml.core.module;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParameterValidatorTest {

    @Test
    void required_missing_returns_violation() {
        var declared = Map.of("region", new YamlModuleParameter(
                ParameterType.STRING, true, null, null, null, null, null, null, List.of(), null));
        var violations = ParameterValidator.validate(declared, Map.of());
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).parameterName()).isEqualTo("region");
        assertThat(violations.get(0).constraint()).isEqualTo("required");
    }

    @Test
    void required_with_default_passes() {
        var declared = Map.of("region", new YamlModuleParameter(
                ParameterType.STRING, true, "us-east", null, null, null, null, null, List.of(), null));
        var violations = ParameterValidator.validate(declared, Map.of());
        assertThat(violations).isEmpty();
    }

    @Test
    void minLength_string_violation() {
        var declared = Map.of("name", new YamlModuleParameter(
                ParameterType.STRING, false, null, 5, null, null, null, null, List.of(), null));
        var violations = ParameterValidator.validate(declared, Map.of("name", "ab"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).constraint()).isEqualTo("minLength");
    }

    @Test
    void maxLength_string_violation() {
        var declared = Map.of("name", new YamlModuleParameter(
                ParameterType.STRING, false, null, null, 3, null, null, null, List.of(), null));
        var violations = ParameterValidator.validate(declared, Map.of("name", "toolong"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).constraint()).isEqualTo("maxLength");
    }

    @Test
    void minLength_list_counts_elements() {
        var declared = Map.of("tags", new YamlModuleParameter(
                ParameterType.LIST, false, null, 3, null, null, null, null, List.of(), null));
        var violations = ParameterValidator.validate(declared, Map.of("tags", "a,b"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).constraint()).isEqualTo("minLength");
    }

    @Test
    void maxLength_list_counts_elements() {
        var declared = Map.of("tags", new YamlModuleParameter(
                ParameterType.LIST, false, null, null, 2, null, null, null, List.of(), null));
        var violations = ParameterValidator.validate(declared, Map.of("tags", "a,b,c"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).constraint()).isEqualTo("maxLength");
    }

    @Test
    void pattern_violation() {
        var declared = Map.of("id", new YamlModuleParameter(
                ParameterType.STRING, false, null, null, null, "^[a-z]+$", null, null, List.of(), null));
        var violations = ParameterValidator.validate(declared, Map.of("id", "ABC123"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).constraint()).isEqualTo("pattern");
    }

    @Test
    void pattern_passes() {
        var declared = Map.of("id", new YamlModuleParameter(
                ParameterType.STRING, false, null, null, null, "^[a-z]+$", null, null, List.of(), null));
        var violations = ParameterValidator.validate(declared, Map.of("id", "abc"));
        assertThat(violations).isEmpty();
    }

    @Test
    void minimum_violation() {
        var declared = Map.of("port", new YamlModuleParameter(
                ParameterType.INTEGER, false, null, null, null, null, 1024, null, List.of(), null));
        var violations = ParameterValidator.validate(declared, Map.of("port", "80"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).constraint()).isEqualTo("minimum");
    }

    @Test
    void maximum_violation() {
        var declared = Map.of("rate", new YamlModuleParameter(
                ParameterType.NUMBER, false, null, null, null, null, null, 1.0, List.of(), null));
        var violations = ParameterValidator.validate(declared, Map.of("rate", "1.5"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).constraint()).isEqualTo("maximum");
    }

    @Test
    void type_parse_error_returns_violation() {
        var declared = Map.of("count", new YamlModuleParameter(
                ParameterType.INTEGER, false, null, null, null, null, null, null, List.of(), null));
        var violations = ParameterValidator.validate(declared, Map.of("count", "abc"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).constraint()).isEqualTo("type");
    }

    @Test
    void collect_all_multiple_violations() {
        var declared = Map.of(
                "name", new YamlModuleParameter(ParameterType.STRING, true, null, null, null, null, null, null, List.of(), null),
                "port", new YamlModuleParameter(ParameterType.INTEGER, false, null, null, null, null, 1024, null, List.of(), null));
        var violations = ParameterValidator.validate(declared, Map.of("port", "80"));
        assertThat(violations).hasSize(2);
    }

    @Test
    void unknown_parameter_returns_violation() {
        var declared = Map.of("region", new YamlModuleParameter(
                ParameterType.STRING, false, null, null, null, null, null, null, List.of(), null));
        var violations = ParameterValidator.validate(declared, Map.of("reigon", "us-east"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).constraint()).isEqualTo("unknown");
        assertThat(violations.get(0).parameterName()).isEqualTo("reigon");
    }

    @Test
    void validateOrThrow_throws_on_violations() {
        var declared = Map.of("x", new YamlModuleParameter(
                ParameterType.STRING, true, null, null, null, null, null, null, List.of(), null));
        assertThatThrownBy(() -> ParameterValidator.validateOrThrow(declared, Map.of()))
                .isInstanceOf(ParameterValidationException.class)
                .satisfies(e -> assertThat(
                        ((ParameterValidationException) e).violations()).hasSize(1));
    }

    @Test
    void validateOrThrow_silent_on_valid() {
        var declared = Map.of("x", new YamlModuleParameter(
                ParameterType.STRING, false, null, null, null, null, null, null, List.of(), null));
        ParameterValidator.validateOrThrow(declared, Map.of("x", "ok"));
    }

    @Test
    void valid_params_return_empty() {
        var declared = Map.of("region", new YamlModuleParameter(
                ParameterType.STRING, true, null, 2, 10, null, null, null, List.of(), null));
        var violations = ParameterValidator.validate(declared, Map.of("region", "us-east"));
        assertThat(violations).isEmpty();
    }

    @Test
    void optional_missing_not_validated() {
        var declared = Map.of("tag", new YamlModuleParameter(
                ParameterType.STRING, false, null, 5, null, null, null, null, List.of(), null));
        var violations = ParameterValidator.validate(declared, Map.of());
        assertThat(violations).isEmpty();
    }

    @Test
    void allowedValues_accepts_valid() {
        var param = new YamlModuleParameter(ParameterType.STRING, true, null,
                                            null, null, null, null, null,
                                            List.of("us-east-1", "eu-west-1", "ap-south-1"), null);
        var violations = ParameterValidator.validate(
                Map.of("region", param), Map.of("region", "eu-west-1"));
        assertThat(violations).isEmpty();
    }

    @Test
    void allowedValues_rejects_invalid() {
        var param = new YamlModuleParameter(ParameterType.STRING, true, null,
                                            null, null, null, null, null,
                                            List.of("us-east-1", "eu-west-1", "ap-south-1"), null);
        var violations = ParameterValidator.validate(
                Map.of("region", param), Map.of("region", "us-west-3"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).constraint()).isEqualTo("allowedValues");
        assertThat(violations.get(0).message())
                .contains("us-west-3")
                .contains("us-east-1");
    }

    @Test
    void allowedValues_empty_skips_check() {
        var param = new YamlModuleParameter(ParameterType.STRING, true, null,
                                            null, null, null, null, null, List.of(), null);
        var violations = ParameterValidator.validate(
                Map.of("x", param), Map.of("x", "anything"));
        assertThat(violations).isEmpty();
    }

    @Test
    void constraintDescription_replaces_message() {
        var param = new YamlModuleParameter(ParameterType.INTEGER, true, null,
                                            null, null, null, 1, 100,
                                            List.of(), "Must be a percentage (1-100)");
        var violations = ParameterValidator.validate(
                Map.of("pct", param), Map.of("pct", "200"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).message())
                .isEqualTo("Must be a percentage (1-100)");
        assertThat(violations.get(0).technicalDetail())
                .contains("200")
                .contains("maximum")
                .contains("100");
    }

    @Test
    void constraintDescription_null_technicalDetail_matches_message() {
        var param = new YamlModuleParameter(ParameterType.INTEGER, true, null,
                                            null, null, null, 1, 100, List.of(), null);
        var violations = ParameterValidator.validate(
                Map.of("pct", param), Map.of("pct", "200"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).technicalDetail())
                .isEqualTo(violations.get(0).message());
    }

    @Test
    void allowedValues_with_constraintDescription() {
        var param = new YamlModuleParameter(ParameterType.STRING, true, null,
                                            null, null, null, null, null,
                                            List.of("us-east-1", "eu-west-1"),
                                            "HA topology requires a US or EU region");
        var violations = ParameterValidator.validate(
                Map.of("region", param), Map.of("region", "ap-south-1"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).message())
                .isEqualTo("HA topology requires a US or EU region");
        assertThat(violations.get(0).technicalDetail())
                .contains("ap-south-1")
                .contains("us-east-1");
    }

    @Test
    void allowedValues_boolean_canonical_match() {
        var param = new YamlModuleParameter(ParameterType.BOOLEAN, true, null,
                                            null, null, null, null, null,
                                            List.of("true"), null);
        var violations = ParameterValidator.validate(
                Map.of("flag", param), Map.of("flag", "yes"));
        assertThat(violations).isEmpty();
    }

    @Test
    void allowedValues_boolean_canonical_reject() {
        var param = new YamlModuleParameter(ParameterType.BOOLEAN, true, null,
                                            null, null, null, null, null,
                                            List.of("true"), null);
        var violations = ParameterValidator.validate(
                Map.of("flag", param), Map.of("flag", "no"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).constraint()).isEqualTo("allowedValues");
    }

    @Test
    void allowedValues_integer_canonical_match() {
        var param = new YamlModuleParameter(ParameterType.INTEGER, true, null,
                                            null, null, null, null, null,
                                            List.of("8080", "9090"), null);
        var violations = ParameterValidator.validate(
                Map.of("port", param), Map.of("port", "8080"));
        assertThat(violations).isEmpty();
    }


// --- fromParamDescriptors bridge ---

    @Test
    void fromDescriptors_converts_basic_param() {
        var descriptors = List.of(Map.<String, Object>of(
                "name", "projectName", "type", "string", "required", true));
        var result = YamlModuleParameter.fromDescriptors(descriptors);
        assertThat(result).containsKey("projectName");
        var param = result.get("projectName");
        assertThat(param.type()).isEqualTo(ParameterType.STRING);
        assertThat(param.required()).isTrue();
        assertThat(param.defaultValue()).isNull();
    }

    @Test
    void fromDescriptors_converts_default_and_enum() {
        var descriptors = List.of(Map.<String, Object>of(
                "name", "template", "type", "string", "required", false,
                "default", "blank", "enum", List.of("blank", "starter", "enterprise")));
        var result = YamlModuleParameter.fromDescriptors(descriptors);
        var param  = result.get("template");
        assertThat(param.defaultValue()).isEqualTo("blank");
        assertThat(param.allowedValues()).containsExactly("blank", "starter", "enterprise");
    }

    @Test
    void fromDescriptors_converts_integer_with_constraints() {
        var descriptors = List.of(Map.<String, Object>of(
                "name", "teamSize", "type", "integer", "required", false,
                "default", 5, "min", 1, "max", 100));
        var result = YamlModuleParameter.fromDescriptors(descriptors);
        var param  = result.get("teamSize");
        assertThat(param.type()).isEqualTo(ParameterType.INTEGER);
        assertThat(param.defaultValue()).isEqualTo("5");
        assertThat(param.minimum()).isEqualTo(1);
        assertThat(param.maximum()).isEqualTo(100);
    }

    @Test
    void fromDescriptors_decimal_maps_to_number() {
        var descriptors = List.of(Map.<String, Object>of(
                "name", "rate", "type", "decimal", "required", false));
        var result = YamlModuleParameter.fromDescriptors(descriptors);
        assertThat(result.get("rate").type()).isEqualTo(ParameterType.NUMBER);
    }

    @Test
    void fromDescriptors_boolean_type() {
        var descriptors = List.of(Map.<String, Object>of(
                "name", "enableCI", "type", "boolean", "required", false, "default", true));
        var result = YamlModuleParameter.fromDescriptors(descriptors);
        var param  = result.get("enableCI");
        assertThat(param.type()).isEqualTo(ParameterType.BOOLEAN);
        assertThat(param.defaultValue()).isEqualTo("true");
    }

    @Test
    void fromDescriptors_multiple_params_preserves_order() {
        var descriptors = List.of(
                Map.<String, Object>of("name", "first", "type", "string", "required", true),
                Map.<String, Object>of("name", "second", "type", "integer", "required", false));
        var result = YamlModuleParameter.fromDescriptors(descriptors);
        assertThat(result.keySet()).containsExactly("first", "second");
    }

    @Test
    void fromDescriptors_with_pattern() {
        var descriptors = List.of(Map.<String, Object>of(
                "name", "slug", "type", "string", "required", true,
                "pattern", "^[a-z-]+$"));
        var result = YamlModuleParameter.fromDescriptors(descriptors);
        assertThat(result.get("slug").pattern()).isEqualTo("^[a-z-]+$");
    }

    @Test
    void fromDescriptors_validates_via_parameterValidator() {
        var descriptors = List.of(Map.<String, Object>of(
                "name", "port", "type", "integer", "required", true,
                "min", 1024, "max", 65535));
        var declared   = YamlModuleParameter.fromDescriptors(descriptors);
        var violations = ParameterValidator.validate(declared, Map.of("port", "80"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).constraint()).isEqualTo("minimum");
    }

    @Test
    void fromDescriptors_empty_returns_empty() {
        var result = YamlModuleParameter.fromDescriptors(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void fromDescriptors_defaults_type_to_string() {
        var descriptors = List.of(Map.<String, Object>of(
                "name", "label", "required", false));
        var result = YamlModuleParameter.fromDescriptors(descriptors);
        assertThat(result.get("label").type()).isEqualTo(ParameterType.STRING);
    }
}
