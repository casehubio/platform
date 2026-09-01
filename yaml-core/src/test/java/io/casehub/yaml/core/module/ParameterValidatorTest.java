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
    void constraintDescription_null_no_technicalDetail() {
        var param = new YamlModuleParameter(ParameterType.INTEGER, true, null,
                                            null, null, null, 1, 100, List.of(), null);
        var violations = ParameterValidator.validate(
                Map.of("pct", param), Map.of("pct", "200"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).technicalDetail()).isNull();
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
}
