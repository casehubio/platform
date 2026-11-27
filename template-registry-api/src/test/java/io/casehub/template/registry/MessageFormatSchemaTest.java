package io.casehub.template.registry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageFormatSchemaTest {

    @Test
    void selectorInfoRequiresNonNull() {
        assertThatThrownBy(() -> new SelectorInfo(null, "integer"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SelectorInfo("count", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void selectorInfoCreation() {
        var info = new SelectorInfo("count", "integer");
        assertThat(info.variableName()).isEqualTo("count");
        assertThat(info.function()).isEqualTo("integer");
    }

    @Test
    void messageFormatSchemaRequiresPattern() {
        assertThatThrownBy(() -> new MessageFormatSchema(null, List.of(), List.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void messageFormatSchemaDefensiveCopy() {
        var selectors = new ArrayList<>(List.of(new SelectorInfo("count", "integer")));
        var variants = new ArrayList<>(List.of("1", "*"));
        var schema = new MessageFormatSchema(".match {$count :integer}", selectors, variants);
        selectors.clear();
        variants.clear();
        assertThat(schema.selectors()).hasSize(1);
        assertThat(schema.variantKeys()).hasSize(2);
    }

    @Test
    void messageFormatSchemaNullListsDefaultToEmpty() {
        var schema = new MessageFormatSchema(".match {$count :integer}", null, null);
        assertThat(schema.selectors()).isEmpty();
        assertThat(schema.variantKeys()).isEmpty();
    }
}
