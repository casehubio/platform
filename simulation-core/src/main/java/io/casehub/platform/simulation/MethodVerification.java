package io.casehub.platform.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class MethodVerification {

    private final String qualifiedName;
    private final InvocationJournal journal;
    private final List<Predicate<JournalEntry>> filters = new ArrayList<>();

    MethodVerification(String qualifiedName, InvocationJournal journal) {
        this.qualifiedName = qualifiedName;
        this.journal = journal;
    }

    public MethodVerification forTenant(String tenancyId) {
        filters.add(e -> tenancyId.equals(e.tenancyId()));
        return this;
    }

    public MethodVerification matching(Predicate<JournalEntry> predicate) {
        filters.add(predicate);
        return this;
    }

    public void wasCalled() {
        long count = matchingCount();
        if (count == 0) {
            throw new AssertionError(
                "Expected \"" + qualifiedName + "\" to be called at least once, but was never called."
                + context());
        }
    }

    public void wasCalled(int exactCount) {
        long count = matchingCount();
        if (count != exactCount) {
            throw new AssertionError(
                "Expected \"" + qualifiedName + "\" to be called exactly " + exactCount
                + " time(s), but was called " + count + " time(s)."
                + matchingDetails());
        }
    }

    public void wasNeverCalled() {
        long count = matchingCount();
        if (count != 0) {
            throw new AssertionError(
                "Expected \"" + qualifiedName + "\" to never be called, but was called "
                + count + " time(s)."
                + matchingDetails());
        }
    }

    public void wasCalledAtLeast(int min) {
        long count = matchingCount();
        if (count < min) {
            throw new AssertionError(
                "Expected \"" + qualifiedName + "\" to be called at least " + min
                + " time(s), but was called " + count + " time(s)."
                + matchingDetails());
        }
    }

    public void wasCalledAtMost(int max) {
        long count = matchingCount();
        if (count > max) {
            throw new AssertionError(
                "Expected \"" + qualifiedName + "\" to be called at most " + max
                + " time(s), but was called " + count + " time(s)."
                + matchingDetails());
        }
    }

    public void allSimulated() {
        List<JournalEntry> entries = matchingEntries();
        if (entries.isEmpty()) {
            throw new AssertionError(
                "Expected \"" + qualifiedName + "\" to have simulated calls, but no calls found."
                + context());
        }
        long nonSimulated = entries.stream().filter(e -> !e.simulated()).count();
        if (nonSimulated > 0) {
            throw new AssertionError(
                "Expected all calls to \"" + qualifiedName + "\" to be simulated, but "
                + nonSimulated + " of " + entries.size() + " were passed through to delegate."
                + matchingDetails());
        }
    }

    public void noneSimulated() {
        List<JournalEntry> entries = matchingEntries();
        if (entries.isEmpty()) {
            throw new AssertionError(
                "Expected \"" + qualifiedName + "\" to have non-simulated calls, but no calls found."
                + context());
        }
        long simulated = entries.stream().filter(JournalEntry::simulated).count();
        if (simulated > 0) {
            throw new AssertionError(
                "Expected no calls to \"" + qualifiedName + "\" to be simulated, but "
                + simulated + " of " + entries.size() + " were simulated."
                + matchingDetails());
        }
    }

    private List<JournalEntry> matchingEntries() {
        return journal.entriesFor(qualifiedName).stream()
                .filter(e -> filters.stream().allMatch(f -> f.test(e)))
                .toList();
    }

    private long matchingCount() {
        return matchingEntries().size();
    }

    private String context() {
        List<JournalEntry> all = journal.entries();
        if (all.isEmpty()) return "\n\nJournal is empty — no calls recorded.";
        return "\n\nOther methods called:\n" + SimulationVerifier.formatSequence(all);
    }

    private String matchingDetails() {
        List<JournalEntry> entries = journal.entriesFor(qualifiedName);
        if (entries.isEmpty()) return context();
        StringBuilder sb = new StringBuilder("\n\nActual calls to \"" + qualifiedName + "\":\n");
        for (int i = 0; i < entries.size(); i++) {
            JournalEntry e = entries.get(i);
            sb.append("  ").append(i + 1).append(". tenant=").append(e.tenancyId())
              .append(" input=").append(e.input())
              .append(" simulated=").append(e.simulated())
              .append(" at=").append(e.timestamp()).append("\n");
        }
        return sb.toString();
    }
}
