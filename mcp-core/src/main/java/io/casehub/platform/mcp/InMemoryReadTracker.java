package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.ReadTracker;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Volatile in-memory {@link ReadTracker}. Data lost on restart.
 * Suitable for single-session agents or testing. For cross-restart
 * persistence, use a SQLite-backed implementation.
 */
public class InMemoryReadTracker implements ReadTracker {

    private final Map<String, Instant> reads = new ConcurrentHashMap<>();

    @Override
    public void recordRead(String agentId, String domain, String resourceKey) {
        reads.put(key(agentId, domain, resourceKey), Instant.now());
    }

    @Override
    public Set<String> readKeys(String agentId, String domain, Instant since) {
        String prefix = agentId + "|" + domain + "|";
        Instant threshold = since != null ? since : Instant.EPOCH;
        return reads.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .filter(e -> !e.getValue().isBefore(threshold))
                .map(e -> e.getKey().substring(prefix.length()))
                .collect(Collectors.toSet());
    }

    private static String key(String agentId, String domain, String resourceKey) {
        return agentId + "|" + domain + "|" + resourceKey;
    }
}
