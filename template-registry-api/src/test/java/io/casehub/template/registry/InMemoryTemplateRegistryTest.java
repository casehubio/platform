package io.casehub.template.registry;

import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTemplateRegistryTest {

    private TemplateNamespace reviewNamespace() {
        var vote = TemplateDefinition.builder("vote")
            .pattern("I ${decision} this ${subject}[ because ${rationale}]")
            .variable("decision", VariableConstraint.enumOf("approve", "reject", "abstain"))
            .variable("subject", VariableConstraint.string())
            .variable("rationale", VariableConstraint.string().optional())
            .build();
        var escalate = TemplateDefinition.builder("escalate")
            .pattern("Escalating to ${target}[ due to ${reason}]")
            .variable("target", VariableConstraint.string())
            .variable("reason", VariableConstraint.string().optional())
            .build();
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("vote", vote);
        templates.put("escalate", escalate);
        return new TemplateNamespace("protocol:review", templates);
    }

    @Test
    void matchExact() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());

        var result = registry.match("protocol:review",
            "I approve this design");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        var matched = (TemplateMatch.Matched) result;
        assertThat(matched.namespace()).isEqualTo("protocol:review");
        assertThat(matched.templateName()).isEqualTo("vote");
        assertThat(matched.fields()).containsEntry("decision", "approve");
        assertThat(matched.score()).isEqualTo(1.0);
    }

    @Test
    void matchGlobalSearchesAllNamespaces() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());

        var result = registry.match("I approve this design");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
    }

    @Test
    void noMatch() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());

        var result = registry.match("protocol:review",
            "Something unrelated");
        assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
    }

    @Test
    void duplicateNamespaceThrows() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());
        assertThatThrownBy(() -> registry.register(reviewNamespace()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deregisterAllowsReRegister() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());
        registry.deregister("protocol:review");
        registry.register(reviewNamespace());
    }

    @Test
    void unknownNamespaceThrows() {
        var registry = new InMemoryTemplateRegistry();
        assertThatThrownBy(() -> registry.match("unknown", "input"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateSlotFilling() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());

        var surface = registry.generate("protocol:review", "vote",
            Map.of("decision", "approve", "subject", "design"));
        assertThat(surface).isEqualTo("I approve this design");
    }

    @Test
    void generateUnknownNamespaceThrows() {
        var registry = new InMemoryTemplateRegistry();
        assertThatThrownBy(() -> registry.generate("unknown", "vote", Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateUnknownTemplateThrows() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());
        assertThatThrownBy(() -> registry.generate(
            "protocol:review", "nonexistent", Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schemaAvailable() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());

        var schema = registry.schema("protocol:review", "vote");
        assertThat(schema).isPresent();
        assertThat(schema.get().patterns()).hasSize(1);
        assertThat(schema.get().variables()).containsKey("decision");
        assertThat(schema.get().structuralSummaries()).isEmpty();
    }

    @Test
    void schemasForNamespace() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());
        assertThat(registry.schemas("protocol:review")).hasSize(2);
    }

    @Test
    void schemasForUnknownNamespaceReturnsEmpty() {
        var registry = new InMemoryTemplateRegistry();
        assertThat(registry.schemas("unknown")).isEmpty();
    }

    @Test
    void constraintValidation() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());

        var result = registry.match("protocol:review",
            "I maybe this design");
        assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
    }

    @Test
    void namespacesReturnsRegistered() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());
        assertThat(registry.namespaces()).containsExactly("protocol:review");
    }

    @Test
    void matchWithOptionalSegment() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());

        var result = registry.match("protocol:review",
            "I approve this architecture because the design is elegant");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        var matched = (TemplateMatch.Matched) result;
        assertThat(matched.fields()).containsEntry("rationale", "the design is elegant");
    }

    @Test
    void concurrentRegistration() throws Exception {
        var registry = new InMemoryTemplateRegistry();
        var threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            final int idx = i;
            threads[i] = Thread.ofVirtual().start(() -> {
                var def = TemplateDefinition.builder("t" + idx)
                    .pattern("Template ${var}")
                    .build();
                var templates = new LinkedHashMap<String, TemplateDefinition>();
                templates.put("t" + idx, def);
                registry.register(new TemplateNamespace("ns:" + idx, templates));
            });
        }
        for (var t : threads) t.join();
        assertThat(registry.namespaces()).hasSize(10);
    }

    @Test
    void bidirectionalRoundtrip() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());

        String input = "I approve this design";
        var matched = (TemplateMatch.Matched) registry.match(
            "protocol:review", input);
        String generated = registry.generate(
            "protocol:review", matched.templateName(), matched.fields());
        assertThat(generated).isEqualTo(input);
    }

    @Test
    void matchWithTraceOnMatchReportsCandidate() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());
        var result = registry.matchWithTrace("protocol:review",
                                             "I approve this design");
        assertThat(result.result()).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(result.trace().bestCandidate()).isEqualTo("protocol:review:vote");
        assertThat(result.trace().bestScore()).isEqualTo(1.0);
        assertThat(result.trace().candidates()).isNotEmpty();
        var matched = result.trace().candidates().stream()
                            .filter(c -> c.outcome() == CandidateOutcome.MATCHED)
                            .findFirst().orElseThrow();
        assertThat(matched.templateName()).isEqualTo("vote");
        assertThat(matched.score()).isEqualTo(1.0);
        assertThat(matched.elements()).isEmpty();
    }

    @Test
    void matchWithTraceOnFailReportsAllCandidates() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());
        var result = registry.matchWithTrace("protocol:review",
                                             "completely unrelated input");
        assertThat(result.result()).isInstanceOf(TemplateMatch.Unmatched.class);
        assertThat(result.trace().bestCandidate()).isNull();
        assertThat(result.trace().candidates()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.trace().candidates()).allMatch(
                c -> c.outcome() == CandidateOutcome.REGEX_FAILED);
    }

    @Test
    void matchWithTraceGlobalSearchesAllNamespaces() {
        var registry = new InMemoryTemplateRegistry();
        registry.register(reviewNamespace());
        var result = registry.matchWithTrace("I approve this design");
        assertThat(result.result()).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(result.trace().candidates()).isNotEmpty();
    }
}
