package io.casehub.platform.simulation.config;

import java.util.Map;

public record TemporalEventConfig(String delay, String label, Map<String, Object> payload) {}
