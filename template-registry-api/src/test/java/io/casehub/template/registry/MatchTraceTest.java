package io.casehub.template.registry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchTraceTest {

    @Test
    void elementTraceConstruction() {
        var trace = new ElementTrace(ElementKind.LITERAL, "approve", "approved", true);
        assertThat(trace.kind()).isEqualTo(ElementKind.LITERAL);
        assertThat(trace.expected()).isEqualTo("approve");
        assertThat(trace.actual()).isEqualTo("approved");
        assertThat(trace.matched()).isTrue();
    }

    @Test
    void elementTraceWithNullActual() {
        var trace = new ElementTrace(ElementKind.LITERAL, "approve", null, false);
        assertThat(trace.actual()).isNull();
        assertThat(trace.matched()).isFalse();
    }

    @Test
    void elementTraceRejectsNullKind() {
        assertThatThrownBy(() -> new ElementTrace(null, "x", "y", true))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void elementTraceRejectsNullExpected() {
        assertThatThrownBy(() -> new ElementTrace(ElementKind.LITERAL, null, "y", true))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void candidateTraceConstruction() {
        var elements = List.of(
            new ElementTrace(ElementKind.LITERAL, "i", "i", true),
            new ElementTrace(ElementKind.VARIABLE, "decision", "approve", true)
        );
        var candidate = new CandidateTrace("ns", "vote", 0, -1.0,
            CandidateOutcome.MATCHED, elements, 1.0);
        assertThat(candidate.namespace()).isEqualTo("ns");
        assertThat(candidate.templateName()).isEqualTo("vote");
        assertThat(candidate.elements()).hasSize(2);
        assertThat(candidate.outcome()).isEqualTo(CandidateOutcome.MATCHED);
    }

    @Test
    void candidateTraceElementsAreDefensivelyCopied() {
        var elements = new ArrayList<>(List.of(
            new ElementTrace(ElementKind.LITERAL, "x", "x", true)));
        var candidate = new CandidateTrace("ns", "t", 0, -1.0,
            CandidateOutcome.MATCHED, elements, 1.0);
        elements.clear();
        assertThat(candidate.elements()).hasSize(1);
    }

    @Test
    void matchTraceConstruction() {
        var candidate = new CandidateTrace("ns", "vote", 0, -1.0,
            CandidateOutcome.MATCHED, List.of(), 1.0);
        var trace = new MatchTrace(List.of(candidate), "ns:vote", 1.0);
        assertThat(trace.candidates()).hasSize(1);
        assertThat(trace.bestCandidate()).isEqualTo("ns:vote");
        assertThat(trace.bestScore()).isEqualTo(1.0);
    }

    @Test
    void matchTraceWithNoBestCandidate() {
        var candidate = new CandidateTrace("ns", "vote", 0, -1.0,
            CandidateOutcome.REGEX_FAILED, List.of(), 0.0);
        var trace = new MatchTrace(List.of(candidate), null, 0.0);
        assertThat(trace.bestCandidate()).isNull();
    }

    @Test
    void matchTraceResultComposition() {
        var match = new TemplateMatch.Unmatched("hello");
        var trace = new MatchTrace(List.of(), null, 0.0);
        var result = new MatchTraceResult(match, trace);
        assertThat(result.result()).isEqualTo(match);
        assertThat(result.trace()).isEqualTo(trace);
    }
}
