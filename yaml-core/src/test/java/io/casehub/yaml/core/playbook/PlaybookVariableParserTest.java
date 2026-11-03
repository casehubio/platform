package io.casehub.yaml.core.playbook;

import io.casehub.yaml.core.type.ValueType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybookVariableParserTest {

    @Test
    void parse_vars_typed_scalars() {
        var vars = new LinkedHashMap<String, Object>();
        vars.put("retryCount:integer", 0);
        vars.put("threshold:number", 0.85);
        var content = Map.<String, Object>of("vars", vars);

        var result = PlaybookVariableParser.parse(content);

        assertThat(result.vars().typeOf("retryCount")).isEqualTo(ValueType.INTEGER);
        assertThat(result.vars().values().get("retryCount")).isEqualTo(0);
        assertThat(result.vars().typeOf("threshold")).isEqualTo(ValueType.NUMBER);
        assertThat(result.vars().values().get("threshold")).isEqualTo(0.85);
    }

    @Test
    void parse_vars_inferred_types() {
        var vars = new LinkedHashMap<String, Object>();
        vars.put("count", 42);
        vars.put("name", "hello");
        vars.put("active", true);
        var content = Map.<String, Object>of("vars", vars);

        var result = PlaybookVariableParser.parse(content);

        assertThat(result.vars().typeOf("count")).isEqualTo(ValueType.INTEGER);
        assertThat(result.vars().typeOf("name")).isEqualTo(ValueType.STRING);
        assertThat(result.vars().typeOf("active")).isEqualTo(ValueType.BOOLEAN);
    }

    @Test
    void parse_vars_domain_type() {
        var vars = new LinkedHashMap<String, Object>();
        vars.put("config:AgentSessionConfig", Map.of("model", "claude-3"));
        var content = Map.<String, Object>of("vars", vars);

        var result = PlaybookVariableParser.parse(content);

        assertThat(result.vars().domainTypes()).containsEntry("config", "AgentSessionConfig");
        assertThat(result.vars().values().get("config")).isInstanceOf(Map.class);
    }

    @Test
    void parse_empty_content_returns_empty() {
        var result = PlaybookVariableParser.parse(Map.of());

        assertThat(result.params()).isEmpty();
        assertThat(result.vars().values()).isEmpty();
    }

    @Test
    void parse_params_required_no_default() {
        var params = new LinkedHashMap<String, Object>();
        params.put("portfolio:string", null);
        var content = Map.<String, Object>of("params", params);

        var result = PlaybookVariableParser.parse(content);

        assertThat(result.params()).containsKey("portfolio");
        assertThat(result.params().get("portfolio").required()).isTrue();
        assertThat(result.params().get("portfolio").defaultValue()).isNull();
    }

    @Test
    void parse_params_optional_with_default() {
        var params = new LinkedHashMap<String, Object>();
        params.put("tier:string", "standard");
        var content = Map.<String, Object>of("params", params);

        var result = PlaybookVariableParser.parse(content);

        assertThat(result.params().get("tier").required()).isFalse();
        assertThat(result.params().get("tier").defaultValue()).isEqualTo("standard");
    }

    @Test
    void parse_params_long_form_with_constraints() {
        var constraints = new LinkedHashMap<String, Object>();
        constraints.put("default", 3);
        constraints.put("minimum", 1);
        constraints.put("maximum", 10);
        var params = new LinkedHashMap<String, Object>();
        params.put("maxRetries:integer", constraints);
        var content = Map.<String, Object>of("params", params);

        var result = PlaybookVariableParser.parse(content);

        var param = result.params().get("maxRetries");
        assertThat(param.defaultValue()).isEqualTo("3");
        assertThat(param.minimum()).isEqualTo(1);
        assertThat(param.maximum()).isEqualTo(10);
        assertThat(param.required()).isFalse();
    }

    @Test
    void parse_params_long_form_required_no_default() {
        var constraints = new LinkedHashMap<String, Object>();
        constraints.put("minimum", 1);
        constraints.put("maximum", 100);
        var params = new LinkedHashMap<String, Object>();
        params.put("retries:integer", constraints);
        var content = Map.<String, Object>of("params", params);

        var result = PlaybookVariableParser.parse(content);

        var param = result.params().get("retries");
        assertThat(param.required()).isTrue();
        assertThat(param.defaultValue()).isNull();
        assertThat(param.minimum()).isEqualTo(1);
    }

    @Test
    void parse_params_long_form_with_allowedValues() {
        var constraints = new LinkedHashMap<String, Object>();
        constraints.put("default", "standard");
        constraints.put("allowedValues", List.of("basic", "standard", "premium"));
        var params = new LinkedHashMap<String, Object>();
        params.put("tier:string", constraints);
        var content = Map.<String, Object>of("params", params);

        var result = PlaybookVariableParser.parse(content);

        var param = result.params().get("tier");
        assertThat(param.allowedValues()).containsExactly("basic", "standard", "premium");
    }

    @Test
    void parse_both_vars_and_params() {
        var vars = new LinkedHashMap<String, Object>();
        vars.put("retryCount:integer", 0);
        var params = new LinkedHashMap<String, Object>();
        params.put("portfolio:string", null);
        var content = Map.<String, Object>of("vars", vars, "params", params);

        var result = PlaybookVariableParser.parse(content);

        assertThat(result.vars().values()).containsKey("retryCount");
        assertThat(result.params()).containsKey("portfolio");
    }
}
