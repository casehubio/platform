package io.casehub.platform.api.mcp;

import java.time.Instant;
import java.util.Map;

/**
 * Published when an MCP tool annotated with {@code @McpTrack(ACCESS)}
 * is invoked. Domain listeners can record provenance from this event.
 */
public record McpAccessEvent(
        String domain,
        String operation,
        Map<String, Object> params,
        Instant timestamp) {
}
