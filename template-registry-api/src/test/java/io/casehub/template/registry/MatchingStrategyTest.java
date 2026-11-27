package io.casehub.template.registry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingStrategyTest {

    @Test
    void defaultMatchWithTraceReturnsEmptyElements() {
        MatchingStrategy strategy = new MatchingStrategy() {
            @Override public String familyId() { return "test"; }
            @Override public Index buildIndex(TemplateNamespace ns, NlpTokenizer tok) { return new Index() {}; }
            @Override public List<PatternRef> selectCandidates(Index idx, List<AnnotatedToken> tokens) { return List.of(); }
            @Override public Optional<MatchResult> match(Index idx, PatternRef ref, List<AnnotatedToken> tokens) {
                return Optional.of(new MatchResult(Map.of("k", "v"), 0.9));
            }
        };
        var index = strategy.buildIndex(null, null);
        var ref = new MatchingStrategy.PatternRef("t", 0);
        var result = strategy.matchWithTrace(index, ref, List.of());
        assertThat(result.result()).isPresent();
        assertThat(result.result().get().score()).isEqualTo(0.9);
        assertThat(result.elements()).isEmpty();
    }

    @Test
    void defaultTraceAllReturnsEmptyList() {
        MatchingStrategy strategy = new MatchingStrategy() {
            @Override public String familyId() { return "test"; }
            @Override public Index buildIndex(TemplateNamespace ns, NlpTokenizer tok) { return new Index() {}; }
            @Override public List<PatternRef> selectCandidates(Index idx, List<AnnotatedToken> tokens) { return List.of(); }
            @Override public Optional<MatchResult> match(Index idx, PatternRef ref, List<AnnotatedToken> tokens) { return Optional.empty(); }
        };
        var traces = strategy.traceAll(new MatchingStrategy.Index() {}, "ns", List.of(), 0.5);
        assertThat(traces).isEmpty();
    }

    @Test
    void defaultStructuralSummariesReturnsEmptyList() {
        MatchingStrategy strategy = new MatchingStrategy() {
            @Override public String familyId() { return "test"; }
            @Override public Index buildIndex(TemplateNamespace ns, NlpTokenizer tok) { return new Index() {}; }
            @Override public List<PatternRef> selectCandidates(Index idx, List<AnnotatedToken> tokens) { return List.of(); }
            @Override public Optional<MatchResult> match(Index idx, PatternRef ref, List<AnnotatedToken> tokens) { return Optional.empty(); }
        };
        var summaries = strategy.structuralSummaries(new MatchingStrategy.Index() {}, "t");
        assertThat(summaries).isEmpty();
    }
}
