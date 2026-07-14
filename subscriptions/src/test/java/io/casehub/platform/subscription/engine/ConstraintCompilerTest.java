package io.casehub.platform.subscription.engine;

import io.casehub.platform.api.subscription.Constraint;
import io.casehub.platform.api.subscription.ConstraintOp;
import io.casehub.platform.api.subscription.SubscribableEvent;
import io.casehub.platform.expression.DefaultExpressionEngineRegistry;
import io.casehub.platform.expression.MvelExpressionEngine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConstraintCompilerTest {

    private final ConstraintCompiler constraintCompiler;

    ConstraintCompilerTest() {
        var registry = new DefaultExpressionEngineRegistry();
        registry.register(new MvelExpressionEngine());
        this.constraintCompiler = new ConstraintCompiler(registry);
    }

    @Test
    void compile_emptyConstraints_tenantIsolationOnly() {
        var fe = constraintCompiler.compile(List.of(), "tenant-1", "user-1");
        assertThat(fe.type()).isEqualTo("mvel");
        assertThat(fe.expression()).startsWith("tenant=tenant-1:");
        var event = new TestEvent("any", "tenant-1");
        assertThat(fe.test(event)).isTrue();
    }

    @Test
    void compile_tenantMismatch_returnsFalse() {
        var fe    = constraintCompiler.compile(List.of(), "tenant-1", "user-1");
        var event = new TestEvent("any", "tenant-2");
        assertThat(fe.test(event)).isFalse();
    }

    @Test
    void compile_eqConstraint_matchingEvent_returnsTrue() {
        var constraints = List.of(new Constraint("type", ConstraintOp.EQ, "alert"));
        var fe          = constraintCompiler.compile(constraints, "tenant-1", "user-1");
        var event       = new TestEvent("alert", "tenant-1");
        assertThat(fe.test(event)).isTrue();
    }

    @Test
    void compile_eqConstraint_nonMatchingEvent_returnsFalse() {
        var constraints = List.of(new Constraint("type", ConstraintOp.EQ, "alert"));
        var fe          = constraintCompiler.compile(constraints, "tenant-1", "user-1");
        var event       = new TestEvent("info", "tenant-1");
        assertThat(fe.test(event)).isFalse();
    }

    @Test
    void compile_neqConstraint_matchingEvent() {
        var constraints = List.of(new Constraint("type", ConstraintOp.NEQ, "alert"));
        var fe          = constraintCompiler.compile(constraints, "tenant-1", "user-1");
        assertThat(fe.test(new TestEvent("info", "tenant-1"))).isTrue();
        assertThat(fe.test(new TestEvent("alert", "tenant-1"))).isFalse();
    }

    @Test
    void compile_singleEqConstraint_expressionContainsParameterizedMvel() {
        var constraints = List.of(new Constraint("type", ConstraintOp.EQ, "completed"));
        var fe          = constraintCompiler.compile(constraints, "tenant-1", "user-1");
        assertThat(fe.type()).isEqualTo("mvel");
        assertThat(fe.expression()).startsWith("tenant=tenant-1:");
        assertThat(fe.expression()).contains("type == $p0");
    }

    @Test
    void compile_meePlaceholder_substitutedWithOwnerId() {
        var constraints = List.of(new Constraint("type", ConstraintOp.EQ, "$me"));
        var fe          = constraintCompiler.compile(constraints, "tenant-1", "owner-42");
        assertThat(fe.expression()).contains("type == $p0");
        var event = new TestEvent("owner-42", "tenant-1");
        assertThat(fe.test(event)).isTrue();
    }

    @Test
    void compile_tenantIdInExpressionString_preventsFilterNodeSharing() {
        var fe1 = constraintCompiler.compile(List.of(), "tenant-A", "user-1");
        var fe2 = constraintCompiler.compile(List.of(), "tenant-B", "user-1");
        assertThat(fe1.expression()).isNotEqualTo(fe2.expression());
        assertThat(fe1.expression()).contains("tenant-A");
        assertThat(fe2.expression()).contains("tenant-B");
    }

    @Test
    void compile_multipleConstraints_allEvaluated() {
        var constraints = List.of(
                new Constraint("type", ConstraintOp.EQ, "alert"),
                new Constraint("type", ConstraintOp.NEQ, "info"));
        var fe = constraintCompiler.compile(constraints, "tenant-1", "user-1");
        assertThat(fe.test(new TestEvent("alert", "tenant-1"))).isTrue();
        assertThat(fe.test(new TestEvent("info", "tenant-1"))).isFalse();
    }

    @Test
    void compile_tenantCheckWithNonSubscribableEvent_returnsFalse() {
        var fe = constraintCompiler.compile(List.of(), "tenant-1", "user-1");
        assertThat(fe.test("no-tenancy-method")).isFalse();
    }

    @Test
    void compile_startsWithOperator() {
        var constraints = List.of(new Constraint("type", ConstraintOp.STARTS_WITH, "al"));
        var fe          = constraintCompiler.compile(constraints, "tenant-1", "user-1");
        assertThat(fe.test(new TestEvent("alert", "tenant-1"))).isTrue();
        assertThat(fe.test(new TestEvent("info", "tenant-1"))).isFalse();
    }

    @Test
    void compile_containsOperator() {
        var constraints = List.of(new Constraint("type", ConstraintOp.CONTAINS, "ler"));
        var fe          = constraintCompiler.compile(constraints, "tenant-1", "user-1");
        assertThat(fe.test(new TestEvent("alert", "tenant-1"))).isTrue();
        assertThat(fe.test(new TestEvent("info", "tenant-1"))).isFalse();
    }

    @Test
    void compile_injectionAttempt_treatedAsData() {
        var constraints = List.of(new Constraint("type", ConstraintOp.EQ,
                                                 "x\"; Runtime.getRuntime().exec(\"id\"); //"));
        var fe    = constraintCompiler.compile(constraints, "tenant-1", "user-1");
        var event = new TestEvent("alert", "tenant-1");
        assertThat(fe.test(event)).isFalse();
    }

    @Test
    void compile_rejectsNullConstraints() {
        assertThatThrownBy(() -> constraintCompiler.compile(null, "tenant-1", "user-1"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void compile_rejectsNullTenancyId() {
        assertThatThrownBy(() -> constraintCompiler.compile(List.of(), null, "user-1"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void compile_rejectsNullOwnerId() {
        assertThatThrownBy(() -> constraintCompiler.compile(List.of(), "tenant-1", null))
                .isInstanceOf(NullPointerException.class);
    }

    record TestEvent(String type, String tenancyId) implements SubscribableEvent {}
}
