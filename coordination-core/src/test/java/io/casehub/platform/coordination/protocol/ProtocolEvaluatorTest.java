package io.casehub.platform.coordination.protocol;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolEvaluatorTest {

    private final ProtocolEvaluator evaluator = new ProtocolEvaluator();

    private static ProtocolDefinition votingProtocol() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("approve", "I ${decision} this design[ because ${rationale}]");
        templates.put("escalate", "I escalate to ${target}");
        return new ProtocolDefinition("voting", templates);
    }

    @Test
    void matchesFirstTemplateWithParams() {
        evaluator.register(votingProtocol());
        var key = new ProtocolInstanceKey("voting", "inst-1");
        var result = evaluator.evaluate(key, "I approve this design because the error handling is solid");
        assertThat(result).isInstanceOf(ProtocolMatchResult.Matched.class);
        var matched = (ProtocolMatchResult.Matched) result;
        assertThat(matched.templateName()).isEqualTo("approve");
        assertThat(matched.params()).containsEntry("decision", "approve");
        assertThat(matched.params()).containsEntry("rationale", "the error handling is solid");
    }

    @Test
    void matchesWithOptionalSegmentAbsent() {
        evaluator.register(votingProtocol());
        var key = new ProtocolInstanceKey("voting", "inst-1");
        var result = evaluator.evaluate(key, "I approve this design");
        assertThat(result).isInstanceOf(ProtocolMatchResult.Matched.class);
        var matched = (ProtocolMatchResult.Matched) result;
        assertThat(matched.templateName()).isEqualTo("approve");
        assertThat(matched.params()).containsEntry("decision", "approve");
        assertThat(matched.params()).doesNotContainKey("rationale");
    }

    @Test
    void matchesSecondTemplate() {
        evaluator.register(votingProtocol());
        var key = new ProtocolInstanceKey("voting", "inst-1");
        var result = evaluator.evaluate(key, "I escalate to the review board");
        assertThat(result).isInstanceOf(ProtocolMatchResult.Matched.class);
        var matched = (ProtocolMatchResult.Matched) result;
        assertThat(matched.templateName()).isEqualTo("escalate");
        assertThat(matched.params()).containsEntry("target", "the review board");
    }

    @Test
    void returnsUnmatchedForNonMatchingResponse() {
        evaluator.register(votingProtocol());
        var key = new ProtocolInstanceKey("voting", "inst-1");
        var result = evaluator.evaluate(key, "I have no opinion");
        assertThat(result).isInstanceOf(ProtocolMatchResult.Unmatched.class);
        var unmatched = (ProtocolMatchResult.Unmatched) result;
        assertThat(unmatched.response()).isEqualTo("I have no opinion");
    }

    @Test
    void multipleProtocolsDispatchCorrectly() {
        evaluator.register(votingProtocol());
        var other = new ProtocolDefinition("ack",
                Map.of("confirm", "I confirm ${item}"));
        evaluator.register(other);

        var voteResult = evaluator.evaluate(
                new ProtocolInstanceKey("voting", "v1"), "I approve this design");
        assertThat(voteResult).isInstanceOf(ProtocolMatchResult.Matched.class);
        assertThat(((ProtocolMatchResult.Matched) voteResult).templateName()).isEqualTo("approve");

        var ackResult = evaluator.evaluate(
                new ProtocolInstanceKey("ack", "a1"), "I confirm the deployment");
        assertThat(ackResult).isInstanceOf(ProtocolMatchResult.Matched.class);
        assertThat(((ProtocolMatchResult.Matched) ackResult).templateName()).isEqualTo("confirm");
    }

    @Test
    void rejectsDuplicateRegistration() {
        evaluator.register(votingProtocol());
        assertThatThrownBy(() -> evaluator.register(votingProtocol()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("voting");
    }

    @Test
    void rejectsEvaluateForUnknownProtocol() {
        var key = new ProtocolInstanceKey("nonexistent", "inst-1");
        assertThatThrownBy(() -> evaluator.evaluate(key, "hello"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void deregisterRemovesProtocol() {
        evaluator.register(votingProtocol());
        assertThat(evaluator.isRegistered("voting")).isTrue();
        evaluator.deregister("voting");
        assertThat(evaluator.isRegistered("voting")).isFalse();
    }

    @Test
    void deregisterAllowsReRegistration() {
        evaluator.register(votingProtocol());
        evaluator.deregister("voting");
        evaluator.register(votingProtocol());
        assertThat(evaluator.isRegistered("voting")).isTrue();
    }

    @Test
    void isRegisteredReturnsFalseForUnknown() {
        assertThat(evaluator.isRegistered("unknown")).isFalse();
    }


    // --- NLP fallback ---

    @Test
    void nlpFallbackMatchesWhenRegexFails() {
        NlpProtocolMatcher mockNlp = (templates, input) ->
                Optional.of(new NlpProtocolMatcher.NlpMatch("approve",
                        Map.of("decision", "approved"), 0.85));
        var eval = new ProtocolEvaluator(mockNlp);
        eval.register(votingProtocol());
        var key = new ProtocolInstanceKey("voting", "inst-1");
        var result = eval.evaluate(key, "I've approved the design changes");
        assertThat(result).isInstanceOf(ProtocolMatchResult.Matched.class);
        var matched = (ProtocolMatchResult.Matched) result;
        assertThat(matched.templateName()).isEqualTo("approve");
        assertThat(matched.params()).containsEntry("decision", "approved");
    }

    @Test
    void regexTakesPrecedenceOverNlp() {
        NlpProtocolMatcher mockNlp = (templates, input) -> {
            throw new AssertionError("NLP should not be called when regex matches");
        };
        var eval = new ProtocolEvaluator(mockNlp);
        eval.register(votingProtocol());
        var key = new ProtocolInstanceKey("voting", "inst-1");
        var result = eval.evaluate(key, "I approve this design");
        assertThat(result).isInstanceOf(ProtocolMatchResult.Matched.class);
        assertThat(((ProtocolMatchResult.Matched) result).templateName()).isEqualTo("approve");
    }

    @Test
    void nlpReturnsUnmatchedWhenNoNlpMatch() {
        NlpProtocolMatcher mockNlp = (templates, input) -> Optional.empty();
        var eval = new ProtocolEvaluator(mockNlp);
        eval.register(votingProtocol());
        var key = new ProtocolInstanceKey("voting", "inst-1");
        var result = eval.evaluate(key, "completely unrelated text");
        assertThat(result).isInstanceOf(ProtocolMatchResult.Unmatched.class);
    }

    @Test
    void noNlpMatcherReturnsUnmatchedOnRegexFail() {
        var eval = new ProtocolEvaluator();
        eval.register(votingProtocol());
        var key = new ProtocolInstanceKey("voting", "inst-1");
        var result = eval.evaluate(key, "I've approved the design changes");
        assertThat(result).isInstanceOf(ProtocolMatchResult.Unmatched.class);
    }

    @Test
    void keywordScopingFiltersCandidates() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("approve", "I ${decision} this design");
        templates.put("schedule", "please schedule ${event} meeting");
        var protocol = new ProtocolDefinition("multi", templates);

        NlpProtocolMatcher mockNlp = (scopedTemplates, input) -> {
            assertThat(scopedTemplates).containsKey("schedule");
            assertThat(scopedTemplates).doesNotContainKey("approve");
            return Optional.of(new NlpProtocolMatcher.NlpMatch("schedule",
                    Map.of("event", "weekly"), 0.7));
        };
        var eval = new ProtocolEvaluator(mockNlp);
        eval.register(protocol);
        var key = new ProtocolInstanceKey("multi", "inst-1");
        var result = eval.evaluate(key, "schedule the weekly meeting please");
        assertThat(result).isInstanceOf(ProtocolMatchResult.Matched.class);
        assertThat(((ProtocolMatchResult.Matched) result).templateName()).isEqualTo("schedule");
    }

    @Test
    void deregisterClearsNlpState() {
        NlpProtocolMatcher mockNlp = (templates, input) ->
                Optional.of(new NlpProtocolMatcher.NlpMatch("approve",
                        Map.of("decision", "yes"), 0.8));
        var eval = new ProtocolEvaluator(mockNlp);
        eval.register(votingProtocol());
        eval.deregister("voting");
        assertThat(eval.isRegistered("voting")).isFalse();
        eval.register(votingProtocol());
        var key = new ProtocolInstanceKey("voting", "inst-1");
        var result = eval.evaluate(key, "I approve this design");
        assertThat(result).isInstanceOf(ProtocolMatchResult.Matched.class);
    }
}
