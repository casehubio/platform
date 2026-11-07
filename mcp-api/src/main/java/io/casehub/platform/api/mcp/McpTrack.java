package io.casehub.platform.api.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds a side effect to the MCP tool — fires a tracking event
 * after the primary method completes. REST and GraphQL do not
 * get the side effect.
 *
 * <p>Example:
 * <pre>
 *   &#64;McpTrack(McpTrackType.ACCESS)
 *   CorpusEntry getEntry(String corpusName, String entryId);
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpTrack {
    McpTrackType value();
}
