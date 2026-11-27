package io.casehub.template.registry;

public enum CandidateOutcome {
    MATCHED,
    KEYWORD_FILTERED,
    REGEX_FAILED,
    STRUCTURAL_FAILED,
    CONSTRAINT_FAILED,
    BELOW_THRESHOLD,
    ADAPTIVE_MATCHED,
    EMBEDDING_RESCUED,
    EMBEDDING_FILTERED
}
