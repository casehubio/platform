package io.casehub.extraction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariableConstraintTest {

    @Test
    void stringConstraintIsRequiredByDefault() {
        var c = VariableConstraint.string();
        assertThat(c).isInstanceOf(VariableConstraint.StringConstraint.class);
        assertThat(((VariableConstraint.StringConstraint) c).required()).isTrue();
    }

    @Test
    void stringConstraintOptional() {
        var c = VariableConstraint.string().optional();
        assertThat(((VariableConstraint.StringConstraint) c).required()).isFalse();
    }

    @Test
    void enumConstraintWithValues() {
        var c = VariableConstraint.enumOf("approve", "reject", "abstain");
        assertThat(c).isInstanceOf(VariableConstraint.EnumConstraint.class);
        var ec = (VariableConstraint.EnumConstraint) c;
        assertThat(ec.values()).containsExactlyInAnyOrder("approve", "reject", "abstain");
        assertThat(ec.required()).isTrue();
    }

    @Test
    void enumConstraintOptional() {
        var c = VariableConstraint.enumOf("yes", "no").optional();
        assertThat(((VariableConstraint.EnumConstraint) c).required()).isFalse();
    }

    @Test
    void enumConstraintRejectsEmpty() {
        assertThatThrownBy(() -> VariableConstraint.enumOf())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enumConstraintValuesAreImmutable() {
        var c = (VariableConstraint.EnumConstraint) VariableConstraint.enumOf("a", "b");
        assertThatThrownBy(() -> c.values().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
