package io.casehub.template.registry.nlp;

import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

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

    @Test
    void matchWithTraceReportsKeywordFiltering() {
        var escalate = TemplateDefinition.builder("escalate")
                                         .pattern("Escalating to ${target} for review")
                                         .variable("target", VariableConstraint.string())
                                         .build();
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("escalate", escalate);
        var escalateRegistry = new NlpTemplateRegistry(new OpenNlpTokenizer());
        escalateRegistry.register(new TemplateNamespace("protocol:escalation", templates));

        var result = escalateRegistry.matchWithTrace("protocol:escalation",
                                                     "The weather is nice");
        assertThat(result.result()).isInstanceOf(TemplateMatch.Unmatched.class);
        var filtered = result.trace().candidates().stream()
                             .filter(c -> c.outcome() == io.casehub.template.registry.CandidateOutcome.KEYWORD_FILTERED)
                             .toList();
        assertThat(filtered).isNotEmpty();
        assertThat(filtered.get(0).keywordAffinity()).isGreaterThanOrEqualTo(0.0);
        assertThat(filtered.get(0).keywordAffinity()).isLessThan(0.3);
    }

    @Test
    void matchWithTraceReportsNlpElements() {
        var result = registry.matchWithTrace("protocol:review",
                                             "I approved this design");
        assertThat(result.result()).isInstanceOf(TemplateMatch.Matched.class);
        var nlpCandidate = result.trace().candidates().stream()
                                 .filter(c -> c.outcome() == io.casehub.template.registry.CandidateOutcome.MATCHED
                                              && !c.elements().isEmpty())
                                 .findFirst();
        assertThat(nlpCandidate).isPresent();
        assertThat(nlpCandidate.get().elements()).isNotEmpty();
    }

    @Test
    void matchWithTraceReportsRegexMatchBeforeNlp() {
        var result = registry.matchWithTrace("protocol:review",
                                             "I approve this design");
        assertThat(result.result()).isInstanceOf(TemplateMatch.Matched.class);
        var matched = result.trace().candidates().stream()
                            .filter(c -> c.outcome() == io.casehub.template.registry.CandidateOutcome.MATCHED)
                            .findFirst().orElseThrow();
        assertThat(matched.score()).isEqualTo(1.0);
        assertThat(matched.elements()).isEmpty();
    }

    @Test
    void matchWithTraceGlobalBestScore() {
        var result = registry.matchWithTrace("I approved this design");
        assertThat(result.result()).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(result.trace().bestCandidate()).isNotNull();
        assertThat(result.trace().bestScore()).isGreaterThan(0.0);
    }

    @Test
    void nearMissOnPartialStructuralMatch() {
        var escalate = TemplateDefinition.builder("escalate")
                                         .pattern("Escalating to ${target} for review")
                                         .variable("target", VariableConstraint.string())
                                         .build();
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("escalate", escalate);
        var nearMissRegistry = new NlpTemplateRegistry(new OpenNlpTokenizer(), 0.1, 0.7);
        nearMissRegistry.register(new TemplateNamespace("protocol:ops", templates));

        var result = nearMissRegistry.match("protocol:ops",
                                            "Escalating to the board");
        assertThat(result).isInstanceOf(TemplateMatch.NearMiss.class);
        var nearMiss = (TemplateMatch.NearMiss) result;
        assertThat(nearMiss.namespace()).isEqualTo("protocol:ops");
        assertThat(nearMiss.suggestedTemplate()).isEqualTo("escalate");
        assertThat(nearMiss.suggestedFormat()).contains("[target]");
    }

    @Test
    void unmatchedStaysUnmatchedForZeroStructuralScore() {
        var result = registry.match("protocol:review",
                                    "The weather is nice");
        assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
    }

    @Test
    void matchedSuppressesNearMiss() {
        var result = registry.match("protocol:review",
                                    "I approve this design");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
    }

    @Test
    void globalMatchReturnsNearMissWhenNoMatch() {
        var escalate = TemplateDefinition.builder("escalate")
                                         .pattern("Escalating to ${target} for review")
                                         .variable("target", VariableConstraint.string())
                                         .build();
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("escalate", escalate);
        var nearMissRegistry = new NlpTemplateRegistry(new OpenNlpTokenizer(), 0.1, 0.7);
        nearMissRegistry.register(new TemplateNamespace("protocol:ops", templates));

        var result = nearMissRegistry.match("Escalating to the board");
        assertThat(result).isInstanceOf(TemplateMatch.NearMiss.class);
        var nearMiss = (TemplateMatch.NearMiss) result;
        assertThat(nearMiss.namespace()).isEqualTo("protocol:ops");
    }

    @Test
    void replaceOrRegister_updatesNlpCompiledCache() {
        registry.deregister("protocol:review");

        var ns1 = new TemplateNamespace("ns", java.util.Map.of("t1",
                                                               TemplateDefinition.builder("t1")
                                                                                 .pattern("I ${decision} this ${subject}")
                                                                                 .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                                                                                 .variable("subject", VariableConstraint.string())
                                                                                 .build()));
        registry.register(ns1);

        var before = registry.match("ns", "I approved this design");
        assertThat(before).isInstanceOf(TemplateMatch.Matched.class);

        var ns2 = new TemplateNamespace("ns", java.util.Map.of("t1",
                                                               TemplateDefinition.builder("t1")
                                                                                 .pattern("${subject} gets my ${decision}")
                                                                                 .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                                                                                 .variable("subject", VariableConstraint.string())
                                                                                 .build()));
        registry.replaceOrRegister(ns2);

        var afterOld = registry.match("ns", "I approved this design");
        assertThat(afterOld).isNotInstanceOf(TemplateMatch.Matched.class);

        var afterNew = registry.match("ns", "design gets my approve");
        assertThat(afterNew).isInstanceOf(TemplateMatch.Matched.class);
    }

}
