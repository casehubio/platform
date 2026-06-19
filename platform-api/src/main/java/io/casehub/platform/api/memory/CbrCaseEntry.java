package io.casehub.platform.api.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Standard schema for a Case-Based Reasoning case written to {@link CaseMemoryStore}.
 *
 * <p>Covers the <em>Retain</em> step of the CBR cycle. Write at case close via
 * {@link #toMemoryInput}; retrieve similar past cases by querying with the new problem
 * description as the {@link MemoryQuery#question()}.
 *
 * <p>{@link #problem()} maps to {@link MemoryInput#text()} — it is the field embedded
 * for semantic similarity search. {@link #solution()}, {@link #outcome()}, and
 * {@link #confidence()} are stored as attributes and retrieved via {@link #from(Memory)}.
 *
 * <p>Context fields (entityId, domain, tenantId, caseId) are supplied at write time by
 * the caller — they are not part of the CBR schema itself.
 *
 * @param problem    natural language description of the problem situation (required)
 * @param solution   natural language description of the action taken (required)
 * @param outcome    result of the solution; null if unknown or not yet resolved
 * @param confidence reliability of the outcome in [0.0, 1.0]; null if not assessed
 */
public record CbrCaseEntry(
        String problem,
        String solution,
        String outcome,
        Double confidence
) {
    public CbrCaseEntry {
        Objects.requireNonNull(problem, "problem required");
        if (problem.isBlank()) throw new IllegalArgumentException("problem must not be blank");
        Objects.requireNonNull(solution, "solution required");
        if (solution.isBlank()) throw new IllegalArgumentException("solution must not be blank");
        if (confidence != null && (confidence < 0.0 || confidence > 1.0))
            throw new IllegalArgumentException("confidence must be in [0,1], got: " + confidence);
    }

    /**
     * Converts this entry to a {@link MemoryInput} for storing in {@link CaseMemoryStore}.
     *
     * <p>{@link #problem()} becomes {@link MemoryInput#text()}.
     * {@link #solution()} is stored under {@link MemoryAttributeKeys#SOLUTION}.
     * {@link #outcome()} is stored under {@link MemoryAttributeKeys#OUTCOME} when non-null.
     * {@link #confidence()} is formatted and stored under {@link MemoryAttributeKeys#CONFIDENCE}
     * when non-null.
     */
    public MemoryInput toMemoryInput(String entityId, MemoryDomain domain,
                                     String tenantId, String caseId) {
        Map<String, String> attrs = new HashMap<>();
        attrs.put(MemoryAttributeKeys.SOLUTION, solution);
        if (outcome != null) attrs.put(MemoryAttributeKeys.OUTCOME, outcome);
        if (confidence != null)
            attrs.put(MemoryAttributeKeys.CONFIDENCE,
                MemoryAttributeKeys.formatConfidence(confidence));
        return new MemoryInput(entityId, domain, tenantId, caseId, problem, attrs);
    }

    /**
     * Extracts a {@link CbrCaseEntry} from a {@link Memory} previously stored via
     * {@link #toMemoryInput}. Returns null for {@link #outcome()} and {@link #confidence()}
     * if the corresponding attributes are absent.
     */
    public static CbrCaseEntry from(Memory memory) {
        Map<String, String> attrs = memory.attributes();
        String confidenceStr = attrs.get(MemoryAttributeKeys.CONFIDENCE);
        Double confidence = confidenceStr != null
            ? MemoryAttributeKeys.parseConfidence(confidenceStr)
            : null;
        return new CbrCaseEntry(
            memory.text(),
            attrs.get(MemoryAttributeKeys.SOLUTION),
            attrs.get(MemoryAttributeKeys.OUTCOME),
            confidence
        );
    }
}
