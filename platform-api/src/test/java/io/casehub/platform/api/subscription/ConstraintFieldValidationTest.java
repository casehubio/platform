package io.casehub.platform.api.subscription;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConstraintFieldValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {"status", "assignee", "priority_level", "item.status",
            "a", "_private", "deep.nested.field", "A1", "_1"})
    void validFieldNames_accepted(String field) {
        var c = new Constraint(field, ConstraintOp.EQ, "val");
        assertThat(c.field()).isEqualTo(field);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"; Runtime.getRuntime().exec(\"id\")",
            "field\"; //",
            ".leading.dot",
            "trailing.",
            "has spaces",
            "has-dash",
            "123startsWithDigit",
            "a..b",
            "field;drop",
            "field()",
            "field[0]",
            "field\"inject",
            "field'inject"
    })
    void invalidFieldNames_rejected(String field) {
        assertThatThrownBy(() -> new Constraint(field, ConstraintOp.EQ, "val"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
    }

    @Test
    void emptyFieldName_rejected() {
        assertThatThrownBy(() -> new Constraint("", ConstraintOp.EQ, "val"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
