package io.casehub.platform.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InvocationJournal {

    private final List<JournalEntry> entries = Collections.synchronizedList(new ArrayList<>());

    public void record(final JournalEntry entry) {
        entries.add(entry);
    }

    public List<JournalEntry> entries() {
        return List.copyOf(entries);
    }

    public List<JournalEntry> entriesFor(final String qualifiedName) {
        return entries.stream()
                .filter(e -> qualifiedName.equals(e.qualifiedName()))
                .toList();
    }

    public long countFor(final String qualifiedName) {
        return entries.stream()
                .filter(e -> qualifiedName.equals(e.qualifiedName()))
                .count();
    }
}
