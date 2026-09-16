package io.casehub.platform.simulation;

import java.time.Instant;

public record JournalEntry(
    String qualifiedName,
    Object input,
    Object output,
    Instant timestamp,
    boolean simulated
) {}
