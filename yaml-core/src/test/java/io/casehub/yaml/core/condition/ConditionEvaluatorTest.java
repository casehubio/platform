package io.casehub.yaml.core.condition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionEvaluatorTest {

    private static final io.casehub.yaml.core.resolver.VariableResolver EMPTY_RESOLVER =
            new io.casehub.yaml.core.resolver.VariableResolver(java.util.Map.of(), java.util.Set.of());

    @Test
    void literal_true_evaluates_without_delegate() {
        var evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("true", EMPTY_RESOLVER, "test")).isTrue();
    }

    @Test
    void literal_false_evaluates_without_delegate() {
        var evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("false", EMPTY_RESOLVER, "test")).isFalse();
    }

    @Test
    void sole_variable_reference_resolved_to_boolean() {
        var resolver = new io.casehub.yaml.core.resolver.VariableResolver(java.util.Map.of(), java.util.Set.of())
                               .withObjectScope("var", name -> "flag".equals(name) ? Boolean.TRUE : null);
        var evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("${var.flag}", resolver, "test")).isTrue();
    }

    @Test
    void sole_variable_reference_resolved_to_string_true() {
        var resolver = new io.casehub.yaml.core.resolver.VariableResolver(java.util.Map.of(), java.util.Set.of())
                               .withObjectScope("var", name -> "flag".equals(name) ? "true" : null);
        var evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("${var.flag}", resolver, "test")).isTrue();
    }

    @Test
    void sole_variable_reference_resolved_to_string_false() {
        var resolver = new io.casehub.yaml.core.resolver.VariableResolver(java.util.Map.of(), java.util.Set.of())
                               .withObjectScope("var", name -> "flag".equals(name) ? "false" : null);
        var evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("${var.flag}", resolver, "test")).isFalse();
    }

    @Test
    void expression_delegates_with_bindings() {
        ConditionDelegate delegate = (expr, bindings) -> {
            return "x > 5".equals(expr) && bindings.containsKey("var");
        };
        var resolver = new io.casehub.yaml.core.resolver.VariableResolver(java.util.Map.of(), java.util.Set.of())
                               .withObjectScope("var", name -> "x".equals(name) ? 10 : null)
                               .withExpressionContext("var", java.util.Map.of("x", 10));
        var evaluator = new ConditionEvaluator(delegate);
        assertThat(evaluator.evaluate("x > 5", resolver, "test")).isTrue();
    }

    @Test
    void no_delegate_throws_for_non_literal() {
        var evaluator = new ConditionEvaluator(null);
        assertThatThrownBy(() -> evaluator.evaluate("x > 5", EMPTY_RESOLVER, "test"))
                .isInstanceOf(ConditionEvaluationException.class)
                .hasMessageContaining("no expression engine configured");
    }

    @Test
    void expression_delegate_receives_resolved_text_plus_bindings() {
        String[]                        captured         = {null};
        java.util.Map<String, Object>[] capturedBindings = new java.util.Map[]{null};
        ConditionDelegate delegate = (expr, bindings) -> {
            captured[0]         = expr;
            capturedBindings[0] = bindings;
            return true;
        };
        var resolver = new io.casehub.yaml.core.resolver.VariableResolver(java.util.Map.of(), java.util.Set.of())
                               .withObjectScope("var", name -> "regime".equals(name) ? "MEAN_REVERTING" : null)
                               .withExpressionContext("var", java.util.Map.of("regime", "MEAN_REVERTING"));
        var evaluator = new ConditionEvaluator(delegate);
        evaluator.evaluate("${var.regime} == 'MEAN_REVERTING'", resolver, "test");
        assertThat(captured[0]).isEqualTo("MEAN_REVERTING == 'MEAN_REVERTING'");
        assertThat(capturedBindings[0]).containsKey("var");
    }

    @Test
    void sole_variable_reference_resolved_to_yaml_yes() {
        var resolver = new io.casehub.yaml.core.resolver.VariableResolver(java.util.Map.of(), java.util.Set.of())
                               .withObjectScope("var", name -> "flag".equals(name) ? "yes" : null);
        var evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("${var.flag}", resolver, "test")).isTrue();
    }

    @Test
    void sole_variable_reference_resolved_to_yaml_no() {
        var resolver = new io.casehub.yaml.core.resolver.VariableResolver(java.util.Map.of(), java.util.Set.of())
                               .withObjectScope("var", name -> "flag".equals(name) ? "no" : null);
        var evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("${var.flag}", resolver, "test")).isFalse();
    }

    @Test
    void sole_variable_reference_resolved_to_yaml_on() {
        var resolver = new io.casehub.yaml.core.resolver.VariableResolver(java.util.Map.of(), java.util.Set.of())
                               .withObjectScope("var", name -> "flag".equals(name) ? "on" : null);
        var evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("${var.flag}", resolver, "test")).isTrue();
    }

    @Test
    void sole_variable_reference_resolved_to_yaml_off() {
        var resolver = new io.casehub.yaml.core.resolver.VariableResolver(java.util.Map.of(), java.util.Set.of())
                               .withObjectScope("var", name -> "flag".equals(name) ? "off" : null);
        var evaluator = new ConditionEvaluator(null);
        assertThat(evaluator.evaluate("${var.flag}", resolver, "test")).isFalse();
    }

    @Test
    void no_delegate_includes_resolved_value_in_error() {
        var resolver = new io.casehub.yaml.core.resolver.VariableResolver(java.util.Map.of(), java.util.Set.of())
                               .withObjectScope("var", name -> "mode".equals(name) ? "production" : null);
        var evaluator = new ConditionEvaluator(null);
        assertThatThrownBy(() -> evaluator.evaluate("${var.mode}", resolver, "test"))
                .isInstanceOf(ConditionEvaluationException.class)
                .hasMessageContaining("production")
                .hasMessageContaining("no expression engine configured");
    }

    @Test
    void expression_delegate_throws_propagates() {
        ConditionDelegate delegate = (expr, bindings) -> {
            throw new RuntimeException("eval failed");
        };
        var evaluator = new ConditionEvaluator(delegate);
        assertThatThrownBy(() -> evaluator.evaluate("bad expression", EMPTY_RESOLVER, "test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("eval failed");
    }
}
