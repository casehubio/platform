package io.casehub.platform.api.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a parameter should be transparently mapped from
 * a human-readable format to an internal representation before
 * the SPI method is invoked.
 *
 * <p>Example: the MCP tool accepts an ISO-8601 timestamp, but the
 * underlying SPI uses an opaque cursor string. The dispatcher
 * performs the mapping so the agent never sees the cursor.
 *
 * <p>Currently supports:
 * <ul>
 *   <li>{@link McpCursorType#INSTANT} — ISO-8601 timestamp mapped
 *       to internal cursor via the SPI's cursor resolution</li>
 * </ul>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpCursorMap {
    McpCursorType value();
}
