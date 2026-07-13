package io.casehub.platform.subscription.engine;

import io.casehub.platform.api.subscription.Constraint;
import io.casehub.platform.api.subscription.ConstraintOp;
import io.casehub.platform.api.subscription.SubscribableEvent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConstraintCompilerTest {

    @Test
    void compile_emptyConstraints_tenantIsolationOnly() {
        var fe = ConstraintCompiler.compile(List.of(), "tenant-1", "user-1");
        assertThat(fe.type()).isEqualTo("mvel");
        assertThat(fe.expression()).startsWith("tenant=tenant-1:");
        // During MVEL mock phase, predicate always returns true for matching tenant
        var event = new TestEvent("any", "tenant-1");
        assertThat(fe.test(event)).isTrue();
    }

    @Test
    void compile_tenantMismatch_returnsFalse() {
        var fe = ConstraintCompiler.compile(List.of(), "tenant-1", "user-1");
        var event = new TestEvent("any", "tenant-2");
        assertThat(fe.test(event)).isFalse();
    }

    @Test
    void compile_singleEqConstraint_expressionContainsMvel() {
        var constraints = List.of(new Constraint("status", ConstraintOp.EQ, "completed"));
        var fe = ConstraintCompiler.compile(constraints, "tenant-1", "user-1");
        assertThat(fe.type()).isEqualTo("mvel");
        assertThat(fe.expression()).startsWith("tenant=tenant-1:");
        assertThat(fe.expression()).contains("status == \"completed\"");
    }

    @Test
    void compile_meePlaceholder_substitutedWithOwnerId() {
        var constraints = List.of(new Constraint("assignee", ConstraintOp.EQ, "$me"));
        var fe = ConstraintCompiler.compile(constraints, "tenant-1", "owner-42");
        assertThat(fe.expression()).contains("assignee == \"owner-42\"");
    }

    @Test
    void compile_inOperator_generatesContainsExpression() {
        var constraints = List.of(new Constraint("priority", ConstraintOp.IN, "high,critical"));
        var fe = ConstraintCompiler.compile(constraints, "tenant-1", "user-1");
        assertThat(fe.expression()).contains("priority");
        assertThat(fe.expression()).contains("IN");
    }

    @Test
    void compile_tenantIdInExpressionString_preventsFilterNodeSharing() {
        var fe1 = ConstraintCompiler.compile(List.of(), "tenant-A", "user-1");
        var fe2 = ConstraintCompiler.compile(List.of(), "tenant-B", "user-1");
        // Different tenants produce different expression strings
        assertThat(fe1.expression()).isNotEqualTo(fe2.expression());
        assertThat(fe1.expression()).contains("tenant-A");
        assertThat(fe2.expression()).contains("tenant-B");
    }

    @Test
    void compile_multipleConstraints_allInExpression() {
        var constraints = List.of(
                new Constraint("status", ConstraintOp.EQ, "completed"),
                new Constraint("priority", ConstraintOp.NEQ, "low"));
        var fe = ConstraintCompiler.compile(constraints, "tenant-1", "user-1");
        assertThat(fe.expression()).contains("status == \"completed\"");
        assertThat(fe.expression()).contains("priority != \"low\"");
    }

    @Test
    void compile_tenantCheckUsesMethodHandle_notMvel() {
        // Tenant isolation works even during MVEL mock phase because it uses MethodHandle
        var fe = ConstraintCompiler.compile(List.of(), "tenant-1", "user-1");
        // Object without tenancyId() method — tenant check fails
        assertThat(fe.test("no-tenancy-method")).isFalse();
    }

    @Test
    void compile_startsWithOperator() {
        var constraints = List.of(new Constraint("name", ConstraintOp.STARTS_WITH, "Work"));
        var fe = ConstraintCompiler.compile(constraints, "tenant-1", "user-1");
        assertThat(fe.expression()).contains("STARTS_WITH");
    }

    @Test
    void compile_containsOperator() {
        var constraints = List.of(new Constraint("name", ConstraintOp.CONTAINS, "item"));
        var fe = ConstraintCompiler.compile(constraints, "tenant-1", "user-1");
        assertThat(fe.expression()).contains("CONTAINS");
    }

    @Test
    void compile_comparisonOperators() {
        var constraints = List.of(
                new Constraint("count", ConstraintOp.GT, "5"),
                new Constraint("level", ConstraintOp.LT, "10"),
                new Constraint("score", ConstraintOp.GTE, "3"),
                new Constraint("rank", ConstraintOp.LTE, "7"));
        var fe = ConstraintCompiler.compile(constraints, "tenant-1", "user-1");
        assertThat(fe.expression()).contains("count > \"5\"");
        assertThat(fe.expression()).contains("level < \"10\"");
        assertThat(fe.expression()).contains("score >= \"3\"");
        assertThat(fe.expression()).contains("rank <= \"7\"");
    }

    @Test
    void compile_rejectsNullConstraints() {
        assertThatThrownBy(() -> ConstraintCompiler.compile(null, "tenant-1", "user-1"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void compile_rejectsNullTenancyId() {
        assertThatThrownBy(() -> ConstraintCompiler.compile(List.of(), null, "user-1"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void compile_rejectsNullOwnerId() {
        assertThatThrownBy(() -> ConstraintCompiler.compile(List.of(), "tenant-1", null))
                .isInstanceOf(NullPointerException.class);
    }

    record TestEvent(String type, String tenancyId) implements SubscribableEvent {}
}
