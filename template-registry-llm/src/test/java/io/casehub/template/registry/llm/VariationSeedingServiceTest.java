package io.casehub.template.registry.llm;

import io.casehub.template.registry.InMemoryTemplateRegistry;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.VariationSeeder;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VariationSeedingServiceTest {

    @Test
    void seedAndRegister_expandsDefinitionWithVariations() {
        var registry = new InMemoryTemplateRegistry();
        VariationSeeder stubSeeder = (def, lang) ->
            List.of("This ${subject} gets my ${decision}");

        var service = new VariationSeedingService(stubSeeder, registry);
        var ns = new TemplateNamespace("ns", Map.of("vote",
            TemplateDefinition.builder("vote")
                .pattern("I ${decision} this ${subject}")
                .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                .variable("subject", VariableConstraint.string())
                .build()));

        service.seedAndRegister(ns);

        var result = registry.match("ns", "This architecture gets my approve");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(((TemplateMatch.Matched) result).fields())
            .containsEntry("decision", "approve")
            .containsEntry("subject", "architecture");
    }

    @Test
    void seedAndRegister_rejectsCollidingVariations() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(new TemplateNamespace("ns1", Map.of("t1",
            TemplateDefinition.builder("t1")
                .pattern("I ${action} this ${thing}")
                .variable("action", VariableConstraint.string())
                .variable("thing", VariableConstraint.string())
                .build())));

        VariationSeeder stubSeeder = (def, lang) ->
            List.of("I ${action} this ${thing}");

        var service = new VariationSeedingService(stubSeeder, registry);
        var ns2 = new TemplateNamespace("ns2", Map.of("t2",
            TemplateDefinition.builder("t2")
                .pattern("${thing} receives my ${action}")
                .variable("action", VariableConstraint.string())
                .variable("thing", VariableConstraint.string())
                .build()));

        service.seedAndRegister(ns2);

        assertThat(registry.schemas("ns2").get(0).patterns()).hasSize(1);
    }

    @Test
    void seedAndRegister_emptySeederRegistersCanonicalOnly() {
        var registry = new InMemoryTemplateRegistry();
        VariationSeeder emptySeeder = (def, lang) -> List.of();

        var service = new VariationSeedingService(emptySeeder, registry);
        var ns = new TemplateNamespace("ns", Map.of("t1",
            TemplateDefinition.builder("t1")
                .pattern("hello ${name}")
                .variable("name", VariableConstraint.string())
                .build()));

        service.seedAndRegister(ns);

        assertThat(registry.schemas("ns").get(0).patterns()).hasSize(1);
        var result = registry.match("ns", "hello world");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
    }
}
