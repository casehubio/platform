package io.casehub.platform.api.memory;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class CbrCaseEntryTest {

    static final MemoryDomain DOMAIN = new MemoryDomain("cbr-test");

    // ── Construction validation ───────────────────────────────────────────────

    @Test
    void construction_requiresNonNullProblem() {
        assertThatNullPointerException()
            .isThrownBy(() -> new CbrCaseEntry(null, "solution", null, null));
    }

    @Test
    void construction_rejectsBlankProblem() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CbrCaseEntry("  ", "solution", null, null));
    }

    @Test
    void construction_requiresNonNullSolution() {
        assertThatNullPointerException()
            .isThrownBy(() -> new CbrCaseEntry("problem", null, null, null));
    }

    @Test
    void construction_rejectsBlankSolution() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CbrCaseEntry("problem", "  ", null, null));
    }

    @Test
    void construction_rejectsConfidenceBelowZero() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CbrCaseEntry("problem", "solution", null, -0.01));
    }

    @Test
    void construction_rejectsConfidenceAboveOne() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new CbrCaseEntry("problem", "solution", null, 1.01));
    }

    @Test
    void construction_acceptsNullOutcomeAndConfidence() {
        var entry = new CbrCaseEntry("problem", "solution", null, null);
        assertThat(entry.problem()).isEqualTo("problem");
        assertThat(entry.solution()).isEqualTo("solution");
        assertThat(entry.outcome()).isNull();
        assertThat(entry.confidence()).isNull();
    }

    @Test
    void construction_acceptsBoundaryConfidenceZero() {
        assertThatCode(() -> new CbrCaseEntry("problem", "solution", null, 0.0))
            .doesNotThrowAnyException();
    }

    @Test
    void construction_acceptsBoundaryConfidenceOne() {
        assertThatCode(() -> new CbrCaseEntry("problem", "solution", null, 1.0))
            .doesNotThrowAnyException();
    }

    // ── toMemoryInput ─────────────────────────────────────────────────────────

    @Test
    void toMemoryInput_problemMapsToText() {
        var entry = new CbrCaseEntry("the situation", "the action", null, null);
        var input = entry.toMemoryInput("e1", DOMAIN, "t1", "case-1");
        assertThat(input.text()).isEqualTo("the situation");
    }

    @Test
    void toMemoryInput_solutionMapsToSolutionAttribute() {
        var entry = new CbrCaseEntry("problem", "applied fix", null, null);
        var input = entry.toMemoryInput("e1", DOMAIN, "t1", "case-1");
        assertThat(input.attributes()).containsEntry(MemoryAttributeKeys.SOLUTION, "applied fix");
    }

    @Test
    void toMemoryInput_outcomeMapsToOutcomeAttributeWhenPresent() {
        var entry = new CbrCaseEntry("problem", "solution", "SUCCESS", null);
        var input = entry.toMemoryInput("e1", DOMAIN, "t1", "case-1");
        assertThat(input.attributes()).containsEntry(MemoryAttributeKeys.OUTCOME, "SUCCESS");
    }

    @Test
    void toMemoryInput_outcomeAbsentFromAttributesWhenNull() {
        var entry = new CbrCaseEntry("problem", "solution", null, null);
        var input = entry.toMemoryInput("e1", DOMAIN, "t1", "case-1");
        assertThat(input.attributes()).doesNotContainKey(MemoryAttributeKeys.OUTCOME);
    }

    @Test
    void toMemoryInput_confidenceMapsToConfidenceAttributeWhenPresent() {
        var entry = new CbrCaseEntry("problem", "solution", null, 0.75);
        var input = entry.toMemoryInput("e1", DOMAIN, "t1", "case-1");
        assertThat(input.attributes()).containsEntry(
            MemoryAttributeKeys.CONFIDENCE, MemoryAttributeKeys.formatConfidence(0.75));
    }

    @Test
    void toMemoryInput_confidenceAbsentFromAttributesWhenNull() {
        var entry = new CbrCaseEntry("problem", "solution", null, null);
        var input = entry.toMemoryInput("e1", DOMAIN, "t1", "case-1");
        assertThat(input.attributes()).doesNotContainKey(MemoryAttributeKeys.CONFIDENCE);
    }

    @Test
    void toMemoryInput_contextFieldsPassedThrough() {
        var entry = new CbrCaseEntry("problem", "solution", null, null);
        var input = entry.toMemoryInput("entity-42", DOMAIN, "tenant-99", "case-7");
        assertThat(input.entityId()).isEqualTo("entity-42");
        assertThat(input.domain()).isEqualTo(DOMAIN);
        assertThat(input.tenantId()).isEqualTo("tenant-99");
        assertThat(input.caseId()).isEqualTo("case-7");
    }

    // ── from(Memory) ──────────────────────────────────────────────────────────

    @Test
    void from_extractsProblemFromText() {
        var memory = memory("the situation", Map.of(MemoryAttributeKeys.SOLUTION, "act"));
        assertThat(CbrCaseEntry.from(memory).problem()).isEqualTo("the situation");
    }

    @Test
    void from_extractsSolutionFromAttribute() {
        var memory = memory("p", Map.of(MemoryAttributeKeys.SOLUTION, "the fix"));
        assertThat(CbrCaseEntry.from(memory).solution()).isEqualTo("the fix");
    }

    @Test
    void from_extractsOutcomeFromAttributeWhenPresent() {
        var memory = memory("p", Map.of(
            MemoryAttributeKeys.SOLUTION, "s",
            MemoryAttributeKeys.OUTCOME, "DONE"));
        assertThat(CbrCaseEntry.from(memory).outcome()).isEqualTo("DONE");
    }

    @Test
    void from_outcomeNullWhenAbsent() {
        var memory = memory("p", Map.of(MemoryAttributeKeys.SOLUTION, "s"));
        assertThat(CbrCaseEntry.from(memory).outcome()).isNull();
    }

    @Test
    void from_extractsConfidenceFromAttributeWhenPresent() {
        var memory = memory("p", Map.of(
            MemoryAttributeKeys.SOLUTION, "s",
            MemoryAttributeKeys.CONFIDENCE, MemoryAttributeKeys.formatConfidence(0.8)));
        assertThat(CbrCaseEntry.from(memory).confidence()).isEqualTo(0.8);
    }

    @Test
    void from_confidenceNullWhenAbsent() {
        var memory = memory("p", Map.of(MemoryAttributeKeys.SOLUTION, "s"));
        assertThat(CbrCaseEntry.from(memory).confidence()).isNull();
    }

    // ── Roundtrip ─────────────────────────────────────────────────────────────

    @Test
    void roundtrip_fullEntry() {
        var original = new CbrCaseEntry("the problem", "the solution", "SUCCESS", 0.9);
        var input = original.toMemoryInput("e1", DOMAIN, "t1", "c1");
        var memory = new Memory("mem-1", input.entityId(), input.domain(), input.tenantId(),
            input.caseId(), input.text(), input.attributes(), Instant.now());
        var recovered = CbrCaseEntry.from(memory);
        assertThat(recovered.problem()).isEqualTo(original.problem());
        assertThat(recovered.solution()).isEqualTo(original.solution());
        assertThat(recovered.outcome()).isEqualTo(original.outcome());
        assertThat(recovered.confidence()).isEqualTo(original.confidence());
    }

    @Test
    void roundtrip_minimalEntry() {
        var original = new CbrCaseEntry("minimal problem", "minimal solution", null, null);
        var input = original.toMemoryInput("e1", DOMAIN, "t1", "c1");
        var memory = new Memory("mem-1", input.entityId(), input.domain(), input.tenantId(),
            input.caseId(), input.text(), input.attributes(), Instant.now());
        var recovered = CbrCaseEntry.from(memory);
        assertThat(recovered).isEqualTo(original);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static Memory memory(String text, Map<String, String> attributes) {
        return new Memory("mem-1", "e1", DOMAIN, "t1", null, text, attributes, Instant.now());
    }
}
