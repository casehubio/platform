package io.casehub.template.registry.nlp.svo;

import io.casehub.template.registry.CandidateOutcome;
import io.casehub.template.registry.MatchingStrategy;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class SvoMatchingStrategyTest {

    private SvoMatchingStrategy strategy;
    private OpenNlpTokenizer tokenizer;

    @BeforeEach
    void setUp() {
        strategy = new SvoMatchingStrategy();
        tokenizer = new OpenNlpTokenizer();
    }

    @Test
    void familyIdIsSvo() {
        assertThat(strategy.familyId()).isEqualTo("svo");
    }

    @Test
    void buildIndexAndMatchEnglish() {
        var vote = TemplateDefinition.builder("vote")
                .pattern("I ${decision} this ${subject}")
                .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                .variable("subject", VariableConstraint.string())
                .build();
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("vote", vote);
        var ns = new TemplateNamespace("protocol:review", templates, "en");

        MatchingStrategy.Index index = strategy.buildIndex(ns, tokenizer);
        var inputTokens = tokenizer.tokenize("I approve this design");
        var candidates = strategy.selectCandidates(index, inputTokens);
        assertThat(candidates).isNotEmpty();

        var result = strategy.match(index, candidates.get(0), inputTokens);
        assertThat(result).isPresent();
        assertThat(result.get().fields()).containsEntry("decision", "approve");
        assertThat(result.get().score()).isGreaterThan(0.5);
    }

    @Test
    void matchWithTraceReturnsElements() {
        var vote = TemplateDefinition.builder("vote")
                .pattern("I ${decision} this ${subject}")
                .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                .variable("subject", VariableConstraint.string())
                .build();
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("vote", vote);
        var ns = new TemplateNamespace("protocol:review", templates, "en");

        var index = strategy.buildIndex(ns, tokenizer);
        var inputTokens = tokenizer.tokenize("I approve this design");
        var candidates = strategy.selectCandidates(index, inputTokens);

        var traceResult = strategy.matchWithTrace(index, candidates.get(0), inputTokens);
        assertThat(traceResult.result()).isPresent();
        assertThat(traceResult.elements()).isNotEmpty();
    }

    @Test
    void traceAllClassifiesOutcomesCorrectly() {
        var vote = TemplateDefinition.builder("vote")
                .pattern("I ${decision} this ${subject}")
                .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                .variable("subject", VariableConstraint.string())
                .build();
        var escalate = TemplateDefinition.builder("escalate")
                .pattern("Escalating to ${target}")
                .variable("target", VariableConstraint.string())
                .build();
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("vote", vote);
        templates.put("escalate", escalate);
        var ns = new TemplateNamespace("protocol:review", templates, "en");

        var index = strategy.buildIndex(ns, tokenizer);
        var inputTokens = tokenizer.tokenize("I approve this design");
        var traces = strategy.traceAll(index, "protocol:review", inputTokens, 0.5);
        assertThat(traces).isNotEmpty();
        assertThat(traces.stream().anyMatch(t -> t.outcome() == CandidateOutcome.MATCHED)).isTrue();
    }

    @Test
    void structuralSummariesReturnData() {
        var vote = TemplateDefinition.builder("vote")
                .pattern("I ${decision} this ${subject}")
                .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                .variable("subject", VariableConstraint.string())
                .build();
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("vote", vote);
        var ns = new TemplateNamespace("protocol:review", templates, "en");

        var index = strategy.buildIndex(ns, tokenizer);
        var summaries = strategy.structuralSummaries(index, "vote");
        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).elements()).isNotEmpty();
    }
}
