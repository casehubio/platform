package io.casehub.platform.api.mcp;

import java.util.Map;

public interface ModelEnricher {
    default String summary() { return ""; }
    default Map<String, Object> state() { return Map.of(); }
}
