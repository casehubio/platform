package io.casehub.template.registry.nlp;

import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NlpTemplateRegistryTest {

    private NlpTemplateRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NlpTemplateRegistry(new OpenNlpTokenizer());
        var vote = TemplateDefinition.builder("vote")
            .pattern("I ${decision} this ${subject}")
            .variable("decision", VariableConstraint.enumOf("approve", "reject"))
            .variable("subject", VariableConstraint.string())
            .build();
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("vote", vote);
        registry.register(new TemplateNamespace("protocol:review", templates));
    }

    @Test
    void regexTierMatchesExact() {
        var result = registry.match("protocol:review", "I approve this design");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        var matched = (TemplateMatch.Matched) result;
        assertThat(matched.score()).isEqualTo(1.0);
        assertThat(matched.fields()).containsEntry("decision", "approve");
    }

    @Test
    void nlpTierMatchesTenseVariation() {
        var result = registry.match("protocol:review", "I approved this design");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        var matched = (TemplateMatch.Matched) result;
        assertThat(matched.score()).isGreaterThan(0.0);
        assertThat(matched.fields()).containsEntry("decision", "approve");
    }

    @Test
    void nlpTierMatchesPluralVariation() {
        var result = registry.match("protocol:review", "I approve these designs");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
    }

    @Test
    void noMatchReturnsUnmatched() {
        var result = registry.match("protocol:review", "The weather is nice");
        assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
    }

    @Test
    void schemaIncludesStructuralSummaries() {
        var schema = registry.schema("protocol:review", "vote");
        assertThat(schema).isPresent();
        assertThat(schema.get().structuralSummaries()).isNotEmpty();
        var summary = schema.get().structuralSummaries().get(0);
        assertThat(summary.elements()).isNotEmpty();
    }

    @Test
    void globalMatchSearchesAllNamespaces() {
        var result = registry.match("I approve this design");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(((TemplateMatch.Matched) result).namespace())
            .isEqualTo("protocol:review");
    }

    @Test
    void deregisterRemovesNlpCompilation() {
        registry.deregister("protocol:review");
        var ack = TemplateDefinition.builder("ack")
            .pattern("Acknowledged by ${actor}")
            .variable("actor", VariableConstraint.string())
            .build();
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("ack", ack);
        registry.register(new TemplateNamespace("protocol:review", templates));

        var result = registry.match("protocol:review", "Acknowledged by Sarah");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
    }

    @Test
    void concurrentRegistration() throws Exception {
        var threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            final int idx = i;
            threads[i] = Thread.ofVirtual().start(() -> {
                var def = TemplateDefinition.builder("t" + idx)
                    .pattern("Template number ${var}")
                    .build();
                var tmps = new LinkedHashMap<String, TemplateDefinition>();
                tmps.put("t" + idx, def);
                registry.register(new TemplateNamespace("ns:" + idx, tmps));
            });
        }
        for (var t : threads) t.join();
        assertThat(registry.namespaces()).hasSize(11);
    }

    @Test
    void enumConstraintRejectsInNlpTier() {
        var result = registry.match("protocol:review", "I celebrated this design");
        assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
    }

    @Test
    void bidirectionalRoundtrip() {
        var result = registry.match("protocol:review", "I approve this design");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        var matched = (TemplateMatch.Matched) result;
        String generated = registry.generate(
            "protocol:review", matched.templateName(), matched.fields());
        assertThat(generated).isEqualTo("I approve this design");
    }

    @Test
    void multiNamespaceNlpMatch() {
        var escalate = TemplateDefinition.builder("escalate")
            .pattern("Escalating to ${target}")
            .variable("target", VariableConstraint.string())
            .build();
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("escalate", escalate);
        registry.register(new TemplateNamespace("protocol:workflow", templates));

        var r1 = registry.match("I approved this architecture");
        assertThat(r1).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(((TemplateMatch.Matched) r1).namespace())
            .isEqualTo("protocol:review");

        var r2 = registry.match("Escalating to the security team");
        assertThat(r2).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(((TemplateMatch.Matched) r2).namespace())
            .isEqualTo("protocol:workflow");
    }
}
