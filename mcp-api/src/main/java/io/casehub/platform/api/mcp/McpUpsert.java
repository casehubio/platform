package io.casehub.platform.api.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Merges an add+update method pair into a single MCP tool that
 * auto-detects whether to create or update. REST and GraphQL
 * keep both methods as separate endpoints.
 *
 * <p>Place on the "add" method. The {@link #existsCheck()} method
 * is called first — if it returns non-null, the {@link #update()}
 * method is called instead of the add. The MCP result includes
 * a {@code created} boolean so the agent knows which path ran.
 *
 * <p>The update method should be annotated with {@link McpExclude}
 * to hide it from the MCP surface.
 *
 * <p>Example:
 * <pre>
 *   &#64;McpUpsert(existsCheck = "getEntry", update = "updateEntry")
 *   String addEntry(String corpusName, String entryId, String body, ...);
 *
 *   &#64;McpExclude
 *   String updateEntry(String corpusName, String entryId, String body, ...);
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpUpsert {
    String existsCheck();
    String update();
}
