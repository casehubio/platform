package io.casehub.platform.api.label;

import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionEvaluationException;
import io.casehub.platform.api.expression.LambdaExpression;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabelRuleTest {

    private static final CompiledExpression<Map<String, Object>, Boolean> ALWAYS_TRUE =
            new LambdaExpression<>(ctx -> true);

    private static final CompiledExpression<Map<String, Object>, Boolean> ALWAYS_FALSE =
            new LambdaExpression<>(ctx -> false);

    private static final CompiledExpression<Map<String, Object>, Boolean> RETURNS_NULL =
            new LambdaExpression<>(ctx -> null);

    private static final CompiledExpression<Map<String, Object>, Boolean> THROWS =
            new LambdaExpression<>(ctx -> { throw new ExpressionEvaluationException("boom"); });

    private static final Map<String, Object> EMPTY_CONTEXT = Map.of();

    // --- Construction ---

    @Test
    void constructor_nullName_throws() {
        assertThatThrownBy(() -> new LabelRule(null, ALWAYS_TRUE, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    @Test
    void constructor_nullCondition_throws() {
        assertThatThrownBy(() -> new LabelRule("rule", null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("condition");
    }

    @Test
    void constructor_actionsDefensivelyCopied() {
        var mutableList = new java.util.ArrayList<LabelAction>(List.of(new LabelAction.Add("x")));
        var rule        = new LabelRule("rule", ALWAYS_TRUE, mutableList);
        mutableList.add(new LabelAction.Remove("y"));
        assertThat(rule.actions()).hasSize(1);
    }

    @Test
    void constructor_actionsImmutable() {
        var rule = new LabelRule("rule", ALWAYS_TRUE, List.of(new LabelAction.Add("x")));
        assertThatThrownBy(() -> rule.actions().add(new LabelAction.Remove("y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- evaluate() ---

    @Test
    void evaluate_allRulesMatch_allActionsReturned() {
        var rule1 = new LabelRule("r1", ALWAYS_TRUE, List.of(new LabelAction.Add("a")));
        var rule2 = new LabelRule("r2", ALWAYS_TRUE, List.of(new LabelAction.Add("b")));
        var actions = LabelRule.evaluate(List.of(rule1, rule2), EMPTY_CONTEXT);
        assertThat(actions).containsExactly(
                new LabelAction.Add("a"),
                new LabelAction.Add("b"));
    }

    @Test
    void evaluate_noRulesMatch_emptyList() {
        var rule = new LabelRule("r1", ALWAYS_FALSE, List.of(new LabelAction.Add("a")));
        var actions = LabelRule.evaluate(List.of(rule), EMPTY_CONTEXT);
        assertThat(actions).isEmpty();
    }

    @Test
    void evaluate_mixedMatch_onlyMatchingActionsReturned() {
        var rule1 = new LabelRule("r1", ALWAYS_TRUE, List.of(new LabelAction.Add("a")));
        var rule2 = new LabelRule("r2", ALWAYS_FALSE, List.of(new LabelAction.Add("b")));
        var rule3 = new LabelRule("r3", ALWAYS_TRUE, List.of(new LabelAction.Remove("c")));
        var actions = LabelRule.evaluate(List.of(rule1, rule2, rule3), EMPTY_CONTEXT);
        assertThat(actions).containsExactly(
                new LabelAction.Add("a"),
                new LabelAction.Remove("c"));
    }

    @Test
    void evaluate_conditionReturnsNull_treatedAsFalse() {
        var rule = new LabelRule("r1", RETURNS_NULL, List.of(new LabelAction.Add("a")));
        var actions = LabelRule.evaluate(List.of(rule), EMPTY_CONTEXT);
        assertThat(actions).isEmpty();
    }

    @Test
    void evaluate_conditionThrows_propagates() {
        var rule = new LabelRule("r1", THROWS, List.of(new LabelAction.Add("a")));
        assertThatThrownBy(() -> LabelRule.evaluate(List.of(rule), EMPTY_CONTEXT))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void evaluate_emptyRulesList_emptyActions() {
        var actions = LabelRule.evaluate(List.of(), EMPTY_CONTEXT);
        assertThat(actions).isEmpty();
    }

    @Test
    void evaluate_ruleWithEmptyActions_noActionsContributed() {
        var rule = new LabelRule("r1", ALWAYS_TRUE, List.of());
        var actions = LabelRule.evaluate(List.of(rule), EMPTY_CONTEXT);
        assertThat(actions).isEmpty();
    }

    @Test
    void evaluate_multipleRulesAddingSameLabel_noDedupBothReturned() {
        var rule1 = new LabelRule("r1", ALWAYS_TRUE, List.of(new LabelAction.Add("x")));
        var rule2 = new LabelRule("r2", ALWAYS_TRUE, List.of(new LabelAction.Add("x")));
        var actions = LabelRule.evaluate(List.of(rule1, rule2), EMPTY_CONTEXT);
        assertThat(actions).containsExactly(
                new LabelAction.Add("x"),
                new LabelAction.Add("x"));
    }

    @Test
    void evaluate_usesContextValues() {
        CompiledExpression<Map<String, Object>, Boolean> checkSeverity =
                new LambdaExpression<>(ctx -> "HIGH".equals(ctx.get("severity")));
        var rule = new LabelRule("r1", checkSeverity, List.of(new LabelAction.Add("urgent")));

        assertThat(LabelRule.evaluate(List.of(rule), Map.of("severity", "HIGH")))
                .containsExactly(new LabelAction.Add("urgent"));
        assertThat(LabelRule.evaluate(List.of(rule), Map.of("severity", "LOW")))
                .isEmpty();
    }

    @Test
    void evaluate_multipleActionsPerRule() {
        var rule = new LabelRule("r1", ALWAYS_TRUE, List.of(
                new LabelAction.Add("queue/urgent"),
                new LabelAction.Remove("queue/normal")));
        var actions = LabelRule.evaluate(List.of(rule), EMPTY_CONTEXT);
        assertThat(actions).containsExactly(
                new LabelAction.Add("queue/urgent"),
                new LabelAction.Remove("queue/normal"));
    }

    @Test
    void evaluate_preservesOrderAcrossRules() {
        var rule1 = new LabelRule("r1", ALWAYS_TRUE, List.of(new LabelAction.Add("a")));
        var rule2 = new LabelRule("r2", ALWAYS_TRUE, List.of(new LabelAction.Add("b")));
        var rule3 = new LabelRule("r3", ALWAYS_TRUE, List.of(new LabelAction.Add("c")));
        var actions = LabelRule.evaluate(List.of(rule1, rule2, rule3), EMPTY_CONTEXT);
        assertThat(actions).extracting(LabelAction::label).containsExactly("a", "b", "c");
    }
}
