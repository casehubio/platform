package io.casehub.extraction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageSchemaTest {

    @Test
    void builderCreatesSchemaWithOneType() {
        var schema = MessageSchema.builder()
                .message("vote")
                    .template("I ${decision} this ${subject}")
                    .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                    .variable("subject", VariableConstraint.string())
                    .done()
                .build();
        assertThat(schema.types()).hasSize(1);
        assertThat(schema.types().get(0).name()).isEqualTo("vote");
        assertThat(schema.types().get(0).templates()).containsExactly("I ${decision} this ${subject}");
        assertThat(schema.types().get(0).variables()).containsKeys("decision", "subject");
    }

    @Test
    void builderCreatesSchemaWithMultipleTypes() {
        var schema = MessageSchema.builder()
                .message("vote")
                    .template("I ${decision}")
                    .done()
                .message("escalate")
                    .template("Escalating to ${target}")
                    .done()
                .build();
        assertThat(schema.types()).hasSize(2);
    }

    @Test
    void emptySchemaRejected() {
        assertThatThrownBy(() -> MessageSchema.builder().build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void typeWithNoTemplateRejected() {
        assertThatThrownBy(() -> MessageSchema.builder()
                .message("vote").done().build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void typesAreImmutable() {
        var schema = MessageSchema.builder()
                .message("vote").template("${x}").done()
                .build();
        assertThatThrownBy(() -> schema.types().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
