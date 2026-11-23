package io.casehub.yaml.step.runner;

import io.casehub.platform.coordination.protocol.NlpProtocolMatcher;
import io.casehub.platform.coordination.protocol.ProtocolDefinition;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateMatchingFilterTest {

    private static TemplateMatchingFilter createFilter(
            Map<String, String> templates,
            Map<String, Map<String, VariableConstraint>> constraints,
            NlpProtocolMatcher nlpMatcher) {
        var protocol = new ProtocolDefinition("test", templates);
        return new TemplateMatchingFilter(protocol, constraints, nlpMatcher);
    }

    @Test
    void matchesRegexTemplate() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision} this design");
        var filter = createFilter(templates, Map.of(), null);
        var result = filter.match("I approve this design");
        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo("vote");
        assertThat(result.get().fields()).containsEntry("decision", "approve");
        assertThat(result.get().score()).isEqualTo(1.0);
        assertThat(result.get().originalInput()).isEqualTo("I approve this design");
    }

    @Test
    void returnsEmptyForUnmatched() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision} this design");
        var filter = createFilter(templates, Map.of(), null);
        assertThat(filter.match("completely unrelated")).isEmpty();
    }

    @Test
    void enforcesEnumConstraint() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision} this design");
        var constraints = Map.of("vote",
                Map.<String, VariableConstraint>of("decision",
                        VariableConstraint.enumOf("approve", "reject")));
        var filter = createFilter(templates, constraints, null);
        assertThat(filter.match("I approve this design")).isPresent();
        assertThat(filter.match("I maybe this design")).isEmpty();
    }

    @Test
    void enumConstraintIsCaseInsensitive() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision} this design");
        var constraints = Map.of("vote",
                Map.<String, VariableConstraint>of("decision",
                        VariableConstraint.enumOf("approve", "reject")));
        var filter = createFilter(templates, constraints, null);
        var result = filter.match("I APPROVE this design");
        assertThat(result).isPresent();
        assertThat(result.get().fields()).containsEntry("decision", "APPROVE");
    }

    @Test
    void nlpFallbackProducesMatchWithScore() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision} this design");
        NlpProtocolMatcher nlp = (t, input) ->
                Optional.of(new NlpProtocolMatcher.NlpMatch("vote",
                        Map.of("decision", "approved"), 0.85));
        var filter = createFilter(templates, Map.of(), nlp);
        var result = filter.match("I've approved the design changes");
        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo("vote");
        assertThat(result.get().fields()).containsEntry("decision", "approved");
    }

    @Test
    void matchWithOptionalSegmentPresent() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision} this design[ because ${rationale}]");
        var filter = createFilter(templates, Map.of(), null);
        var result = filter.match("I approve this design because it's solid");
        assertThat(result).isPresent();
        assertThat(result.get().fields()).containsEntry("rationale", "it's solid");
    }

    @Test
    void matchWithOptionalSegmentAbsent() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision} this design[ because ${rationale}]");
        var filter = createFilter(templates, Map.of(), null);
        var result = filter.match("I approve this design");
        assertThat(result).isPresent();
        assertThat(result.get().fields()).doesNotContainKey("rationale");
    }

    @Test
    void multipleTemplatesFirstMatchWins() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision} this design");
        templates.put("escalate", "Escalating to ${target}");
        var filter = createFilter(templates, Map.of(), null);

        var voteResult = filter.match("I approve this design");
        assertThat(voteResult).isPresent();
        assertThat(voteResult.get().type()).isEqualTo("vote");

        var escalateResult = filter.match("Escalating to the review board");
        assertThat(escalateResult).isPresent();
        assertThat(escalateResult.get().type()).isEqualTo("escalate");
    }

    @Test
    void noConstraintTemplateMatchesAnyValue() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("ack", "I confirm ${item}");
        var filter = createFilter(templates, Map.of(), null);
        var result = filter.match("I confirm the deployment");
        assertThat(result).isPresent();
        assertThat(result.get().fields()).containsEntry("item", "the deployment");
    }


    // --- evaluate() with TemplateMatchResult ---

    @Test
    void evaluateReturnsMatchedForRegexHit() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision} this design");
        var filter = createFilter(templates, Map.of(), null);
        var result = filter.evaluate("I approve this design");
        assertThat(result).isInstanceOf(TemplateMatchResult.Matched.class);
        var matched = (TemplateMatchResult.Matched) result;
        assertThat(matched.event().type()).isEqualTo("vote");
    }

    @Test
    void evaluateReturnsPassThroughForNoAffinity() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision} this design");
        var filter = createFilter(templates, Map.of(), null);
        var result = filter.evaluate("let's schedule a meeting");
        assertThat(result).isInstanceOf(TemplateMatchResult.PassThrough.class);
        assertThat(((TemplateMatchResult.PassThrough) result).input()).isEqualTo("let's schedule a meeting");
    }

    @Test
    void evaluateReturnsNearMissForKeywordAffinity() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision} this design");
        var filter = createFilter(templates, Map.of(), null);
        // "design" is a keyword from the template — this input shares it
        var result = filter.evaluate("the design looks good to me");
        assertThat(result).isInstanceOf(TemplateMatchResult.NearMiss.class);
        var nearMiss = (TemplateMatchResult.NearMiss) result;
        assertThat(nearMiss.suggestedTemplate()).isEqualTo("vote");
        assertThat(nearMiss.suggestedFormat()).contains("[decision]");
        assertThat(nearMiss.suggestedFormat()).contains("design");
    }

    @Test
    void rephraseDisabledReturnsPassThroughInsteadOfNearMiss() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision} this design");
        var protocol = new ProtocolDefinition("test", templates);
        var filter = new TemplateMatchingFilter(protocol, Map.of(), null, false);
        var result = filter.evaluate("the design looks good to me");
        assertThat(result).isInstanceOf(TemplateMatchResult.PassThrough.class);
    }

    @Test
    void nearMissFormatsSuggestionFromPattern() {
        String format = TemplateMatchingFilter.formatSuggestion("I ${decision} this ${subject}[ because ${rationale}]");
        assertThat(format).isEqualTo("I [decision] this [subject] because [rationale]");
    }

    @Test
    void constraintFailureTriggerNearMissWhenAffinityPresent() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision} this design");
        var constraints = Map.of("vote",
                Map.<String, VariableConstraint>of("decision",
                        VariableConstraint.enumOf("approve", "reject")));
        var filter = createFilter(templates, constraints, null);
        // Regex matches but enum constraint fails — "maybe" is not in [approve, reject]
        // "design" keyword gives affinity → near-miss
        var result = filter.evaluate("I maybe this design");
        assertThat(result).isInstanceOf(TemplateMatchResult.NearMiss.class);
    }

    // --- match() backward compatibility ---

    @Test
    void matchStillReturnsOptionalForBackwardCompat() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision} this design");
        var filter = createFilter(templates, Map.of(), null);
        assertThat(filter.match("I approve this design")).isPresent();
        assertThat(filter.match("completely unrelated")).isEmpty();
    }
}
