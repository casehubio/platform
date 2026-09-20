package io.casehub.platform.simulation.event.quarkus;

import java.util.Map;

public record TemporalEventInput(
        String delay,
        String label,
        Map<String, Object> payload) {}
