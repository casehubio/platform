package io.casehub.platform.simulation;

import java.time.Instant;

public record JournalEntry(
    String qualifiedName,
    String tenancyId,
    Object input,
    Object output,
    Instant timestamp,
    boolean simulated
) {}
