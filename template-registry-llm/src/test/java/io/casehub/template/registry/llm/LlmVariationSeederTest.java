package io.casehub.template.registry.llm;

import io.casehub.template.registry.TemplateDefinition;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmVariationSeederTest {

    @Test
    void parseAndValidate_acceptsValidVariations() {
        var seeder = new LlmVariationSeeder(null, 5);
        var vars = Map.<String, VariableConstraint>of(
            "decision", VariableConstraint.enumOf("approve", "reject"),
            "subject", VariableConstraint.string());

        var result = seeder.parseAndValidate(
            "[\"This ${subject} gets my ${decision}\", \"My ${decision} goes to this ${subject}\"]",
            "I ${decision} this ${subject}", vars);

        assertThat(result).containsExactly(
            "This ${subject} gets my ${decision}",
            "My ${decision} goes to this ${subject}");
    }

    @Test
    void parseAndValidate_filtersVariationsMissingVariable() {
        var seeder = new LlmVariationSeeder(null, 5);
        var vars = Map.<String, VariableConstraint>of(
            "decision", VariableConstraint.enumOf("approve", "reject"),
            "subject", VariableConstraint.string());

        var result = seeder.parseAndValidate(
            "[\"This ${subject} is good\", \"This ${subject} gets my ${decision}\"]",
            "I ${decision} this ${subject}", vars);

        assertThat(result).containsExactly("This ${subject} gets my ${decision}");
    }

    @Test
    void parseAndValidate_filtersOriginalPattern() {
        var seeder = new LlmVariationSeeder(null, 5);
        var vars = Map.<String, VariableConstraint>of("name", VariableConstraint.string());

        var result = seeder.parseAndValidate(
            "[\"hello ${name}\", \"hi ${name}\"]", "hello ${name}", vars);

        assertThat(result).containsExactly("hi ${name}");
    }

    @Test
    void parseAndValidate_handlesCodeFencedResponse() {
        var seeder = new LlmVariationSeeder(null, 5);
        var vars = Map.<String, VariableConstraint>of("name", VariableConstraint.string());

        var result = seeder.parseAndValidate(
            "```json\n[\"hi ${name}\", \"hey ${name}\"]\n```",
            "hello ${name}", vars);

        assertThat(result).containsExactly("hi ${name}", "hey ${name}");
    }

    @Test
    void parseAndValidate_returnsEmptyOnInvalidJson() {
        var seeder = new LlmVariationSeeder(null, 5);
        var vars = Map.<String, VariableConstraint>of("name", VariableConstraint.string());

        var result = seeder.parseAndValidate("not json at all", "hello ${name}", vars);
        assertThat(result).isEmpty();
    }

    @Test
    void parseAndValidate_respectsMaxLimit() {
        var seeder = new LlmVariationSeeder(null, 2);
        var vars = Map.<String, VariableConstraint>of("name", VariableConstraint.string());

        var result = seeder.parseAndValidate(
            "[\"hi ${name}\", \"hey ${name}\", \"yo ${name}\", \"hola ${name}\"]",
            "hello ${name}", vars);

        assertThat(result).hasSize(2);
    }
}
