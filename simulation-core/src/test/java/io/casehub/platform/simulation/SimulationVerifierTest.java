package io.casehub.platform.simulation;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationVerifierTest {

    private InvocationJournal journalWith(JournalEntry... entries) {
        var journal = new InvocationJournal();
        for (var e : entries) journal.record(e);
        return journal;
    }

    private JournalEntry entry(String qn) {
        return new JournalEntry(qn, "t1", "in", "out", Instant.now(), true);
    }

    // --- Count assertions ---

    @Test
    void wasCalled_passes_when_method_called_at_least_once() {
        var journal = journalWith(entry("spi.a"));
        SimulationVerifier.on(journal).method("spi.a").wasCalled();
    }

    @Test
    void wasCalled_fails_when_method_never_called() {
        var journal = journalWith(entry("spi.b"));
        assertThatThrownBy(() ->
            SimulationVerifier.on(journal).method("spi.a").wasCalled()
        ).isInstanceOf(AssertionError.class);
    }

    @Test
    void wasCalled_exact_count_passes() {
        var journal = journalWith(entry("spi.a"), entry("spi.a"), entry("spi.a"));
        SimulationVerifier.on(journal).method("spi.a").wasCalled(3);
    }

    @Test
    void wasCalled_exact_count_fails_on_mismatch() {
        var journal = journalWith(entry("spi.a"), entry("spi.a"));
        assertThatThrownBy(() ->
            SimulationVerifier.on(journal).method("spi.a").wasCalled(3)
        ).isInstanceOf(AssertionError.class);
    }

    @Test
    void wasNeverCalled_passes_when_absent() {
        var journal = journalWith(entry("spi.b"));
        SimulationVerifier.on(journal).method("spi.a").wasNeverCalled();
    }

    @Test
    void wasNeverCalled_fails_when_present() {
        var journal = journalWith(entry("spi.a"));
        assertThatThrownBy(() ->
            SimulationVerifier.on(journal).method("spi.a").wasNeverCalled()
        ).isInstanceOf(AssertionError.class);
    }

    @Test
    void wasCalledAtLeast_passes() {
        var journal = journalWith(entry("spi.a"), entry("spi.a"), entry("spi.a"));
        SimulationVerifier.on(journal).method("spi.a").wasCalledAtLeast(2);
    }

    @Test
    void wasCalledAtMost_passes() {
        var journal = journalWith(entry("spi.a"));
        SimulationVerifier.on(journal).method("spi.a").wasCalledAtMost(3);
    }

    // --- Filtering ---

    @Test
    void forTenant_filters_by_tenancy() {
        var journal = journalWith(
            new JournalEntry("spi.a", "t1", "in1", "out1", Instant.now(), true),
            new JournalEntry("spi.a", "t2", "in2", "out2", Instant.now(), true),
            new JournalEntry("spi.a", "t1", "in3", "out3", Instant.now(), true)
        );
        SimulationVerifier.on(journal).method("spi.a").forTenant("t1").wasCalled(2);
        SimulationVerifier.on(journal).method("spi.a").forTenant("t2").wasCalled(1);
    }

    @Test
    void matching_filters_by_predicate() {
        var journal = journalWith(
            new JournalEntry("spi.a", "t1", "alpha", "out", Instant.now(), true),
            new JournalEntry("spi.a", "t1", "beta", "out", Instant.now(), true),
            new JournalEntry("spi.a", "t1", "alpha", "out", Instant.now(), true)
        );
        SimulationVerifier.on(journal).method("spi.a")
            .matching(e -> "alpha".equals(e.input()))
            .wasCalled(2);
    }

    @Test
    void chained_filters_apply_with_and_semantics() {
        var journal = journalWith(
            new JournalEntry("spi.a", "t1", "alpha", "out", Instant.now(), true),
            new JournalEntry("spi.a", "t2", "alpha", "out", Instant.now(), true),
            new JournalEntry("spi.a", "t1", "beta", "out", Instant.now(), true)
        );
        SimulationVerifier.on(journal).method("spi.a")
            .forTenant("t1")
            .matching(e -> "alpha".equals(e.input()))
            .wasCalled(1);
    }

    // --- Order verification ---

    @Test
    void inOrder_passes_for_correct_sequence() {
        var journal = journalWith(entry("spi.a"), entry("spi.b"), entry("spi.c"));
        SimulationVerifier.on(journal).inOrder("spi.a", "spi.b", "spi.c");
    }

    @Test
    void inOrder_passes_with_interleaved_calls() {
        var journal = journalWith(entry("spi.a"), entry("spi.x"), entry("spi.b"));
        SimulationVerifier.on(journal).inOrder("spi.a", "spi.b");
    }

    @Test
    void inOrder_fails_for_wrong_sequence() {
        var journal = journalWith(entry("spi.b"), entry("spi.a"));
        assertThatThrownBy(() ->
            SimulationVerifier.on(journal).inOrder("spi.a", "spi.b")
        ).isInstanceOf(AssertionError.class);
    }

    // --- Exhaustive verification ---

    @Test
    void noUnverifiedCalls_passes_when_all_verified() {
        var journal = journalWith(entry("spi.a"), entry("spi.b"));
        var verifier = SimulationVerifier.on(journal);
        verifier.method("spi.a").wasCalled();
        verifier.method("spi.b").wasCalled();
        verifier.noUnverifiedCalls();
    }

    @Test
    void noUnverifiedCalls_fails_when_unverified_exist() {
        var journal = journalWith(entry("spi.a"), entry("spi.b"));
        var verifier = SimulationVerifier.on(journal);
        verifier.method("spi.a").wasCalled();
        assertThatThrownBy(verifier::noUnverifiedCalls)
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("spi.b");
    }

    // --- Simulation path ---

    @Test
    void allSimulated_passes_when_all_simulated() {
        var journal = journalWith(
            new JournalEntry("spi.a", "t1", "in", "out", Instant.now(), true),
            new JournalEntry("spi.a", "t1", "in2", "out2", Instant.now(), true)
        );
        SimulationVerifier.on(journal).method("spi.a").allSimulated();
    }

    @Test
    void allSimulated_fails_when_some_not_simulated() {
        var journal = journalWith(
            new JournalEntry("spi.a", "t1", "in", "out", Instant.now(), true),
            new JournalEntry("spi.a", "t1", "in2", "out2", Instant.now(), false)
        );
        assertThatThrownBy(() ->
            SimulationVerifier.on(journal).method("spi.a").allSimulated()
        ).isInstanceOf(AssertionError.class);
    }

    @Test
    void noneSimulated_passes_when_all_passthrough() {
        var journal = journalWith(
            new JournalEntry("spi.a", "t1", "in", "out", Instant.now(), false)
        );
        SimulationVerifier.on(journal).method("spi.a").noneSimulated();
    }

    // --- Error messages ---

    @Test
    void error_message_includes_actual_calls() {
        var journal = journalWith(
            new JournalEntry("spi.a", "t1", "input-val", "out", Instant.now(), true)
        );
        assertThatThrownBy(() ->
            SimulationVerifier.on(journal).method("spi.a").wasCalled(5)
        ).isInstanceOf(AssertionError.class)
         .hasMessageContaining("input-val")
         .hasMessageContaining("t1");
    }

    // --- Edge cases ---

    @Test
    void empty_journal_wasNeverCalled_passes() {
        var journal = new InvocationJournal();
        SimulationVerifier.on(journal).method("spi.a").wasNeverCalled();
    }

    @Test
    void empty_journal_noUnverifiedCalls_passes() {
        var journal = new InvocationJournal();
        SimulationVerifier.on(journal).noUnverifiedCalls();
    }
}
