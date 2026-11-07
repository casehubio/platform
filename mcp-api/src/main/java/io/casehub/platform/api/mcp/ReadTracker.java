package io.casehub.platform.api.mcp;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Tracks which resources an agent has accessed via MCP tools.
 * Enables "what haven't I seen?" queries for agents maintaining
 * awareness over a body of knowledge across sessions.
 *
 * <p>Keyed by agent identity ({@code agentId} from
 * {@code CurrentPrincipal.actorId()}), domain (the {@code @McpDomain}
 * value), and a resource key (domain-specific — e.g. entry ID).
 *
 * <p>Platform utility, not a mandatory SPI. Domains opt in by
 * depending on an implementation module and wiring it to
 * {@link McpAccessEvent} listeners.
 */
public interface ReadTracker {

    void recordRead(String agentId, String domain, String resourceKey);

    Set<String> readKeys(String agentId, String domain, Instant since);

    default List<String> unread(String agentId, String domain,
                                 List<String> allKeys, Instant since) {
        Set<String> read = readKeys(agentId, domain, since);
        return allKeys.stream().filter(k -> !read.contains(k)).toList();
    }
}
