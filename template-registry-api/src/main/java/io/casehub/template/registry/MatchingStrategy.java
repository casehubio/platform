package io.casehub.template.registry;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MatchingStrategy {

    String familyId();

    Index buildIndex(TemplateNamespace namespace, NlpTokenizer tokenizer);

    List<PatternRef> selectCandidates(Index index,
                                      List<AnnotatedToken> inputTokens);

    Optional<MatchResult> match(Index index, PatternRef candidate,
                                 List<AnnotatedToken> inputTokens);

    record TraceableMatchResult(Optional<MatchResult> result,
                                 List<ElementTrace> elements) {}

    default TraceableMatchResult matchWithTrace(Index index, PatternRef candidate,
                                                 List<AnnotatedToken> inputTokens) {
        Optional<MatchResult> result = match(index, candidate, inputTokens);
        return new TraceableMatchResult(result, List.of());
    }

    default List<CandidateTrace> traceAll(Index index,
                                           String namespaceName,
                                           List<AnnotatedToken> inputTokens,
                                           double scoreThreshold) {
        return List.of();
    }

    default List<TemplateSchema.StructuralSummary> structuralSummaries(
            Index index, String templateName) {
        return List.of();
    }

    interface Index {}

    record PatternRef(String templateName, int patternIndex) {}

    record MatchResult(Map<String, String> fields, double score) {}
}
