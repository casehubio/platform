package io.casehub.platform.simulation.config;

import java.util.List;

public record TemporalProfileConfig(
        String qualifiedName,
        String tenancyId,
        boolean loop,
        double speed,
        List<TemporalEventConfig> events,
        String eventsFile,
        String fromCorpus,
        List<SequenceRef> sequence) {

    public TemporalProfileConfig {
        int sourceCount = (events != null && !events.isEmpty() ? 1 : 0)
                + (eventsFile != null ? 1 : 0)
                + (fromCorpus != null ? 1 : 0)
                + (sequence != null && !sequence.isEmpty() ? 1 : 0);
        if (sourceCount > 1) {
            throw new IllegalArgumentException(
                    "Temporal profile must have exactly one source (events, events-file, from-corpus, or sequence), found " + sourceCount);
        }
    }
}
