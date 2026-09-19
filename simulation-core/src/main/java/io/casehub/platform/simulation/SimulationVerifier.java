package io.casehub.platform.simulation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class SimulationVerifier {

    private final InvocationJournal journal;
    private final Set<String> verifiedMethods = new HashSet<>();

    private SimulationVerifier(InvocationJournal journal) {
        this.journal = journal;
    }

    public static SimulationVerifier on(InvocationJournal journal) {
        return new SimulationVerifier(journal);
    }

    public static SimulationVerifier on(SimulationOverlay overlay) {
        return new SimulationVerifier(overlay.journal());
    }

    public MethodVerification method(String qualifiedName) {
        verifiedMethods.add(qualifiedName);
        return new MethodVerification(qualifiedName, journal);
    }

    public void inOrder(String... qualifiedNames) {
        List<JournalEntry> entries = journal.entries();
        int searchFrom = 0;
        for (String qn : qualifiedNames) {
            boolean found = false;
            for (int i = searchFrom; i < entries.size(); i++) {
                if (qn.equals(entries.get(i).qualifiedName())) {
                    searchFrom = i + 1;
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new AssertionError(
                    "Expected calls in order " + String.join(" → ", qualifiedNames)
                    + " but \"" + qn + "\" was not found after position " + searchFrom
                    + ".\n\nActual call sequence:\n" + formatSequence(entries));
            }
        }
    }

    public void noUnverifiedCalls() {
        Set<String> allMethods = journal.entries().stream()
                .map(JournalEntry::qualifiedName)
                .collect(Collectors.toSet());
        Set<String> unverified = new HashSet<>(allMethods);
        unverified.removeAll(verifiedMethods);
        if (!unverified.isEmpty()) {
            StringBuilder sb = new StringBuilder("Unverified calls found in journal:\n");
            for (String qn : unverified) {
                sb.append("  - ").append(qn).append(": ")
                  .append(journal.countFor(qn)).append(" call(s)\n");
            }
            throw new AssertionError(sb.toString());
        }
    }

    static String formatSequence(List<JournalEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            JournalEntry e = entries.get(i);
            sb.append("  ").append(i + 1).append(". ").append(e.qualifiedName())
              .append(" tenant=").append(e.tenancyId())
              .append(" simulated=").append(e.simulated())
              .append(" at=").append(e.timestamp()).append("\n");
        }
        return sb.toString();
    }
}
