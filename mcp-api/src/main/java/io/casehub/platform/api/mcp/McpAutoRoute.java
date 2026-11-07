package io.casehub.platform.api.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Merges two related methods into a single MCP tool that
 * auto-detects which path to take. REST and GraphQL keep
 * both methods as separate endpoints.
 *
 * <p>Place on the primary method. The {@link #check()} method
 * is called first — if it returns non-null, the {@link #alternate()}
 * method is called instead. The MCP result includes a
 * {@code created} boolean so the agent knows which path ran.
 *
 * <p>The alternate method should be annotated with {@link McpExclude}
 * to hide it from the MCP surface.
 *
 * <p>Common patterns:
 * <ul>
 *   <li>Create-or-update (upsert): check = "get", alternate = "update"</li>
 *   <li>Idempotent apply: check = "exists", alternate = "replace"</li>
 * </ul>
 *
 * <p>Example:
 * <pre>
 *   &#64;McpAutoRoute(check = "getEntry", alternate = "updateEntry")
 *   String addEntry(String corpusName, String entryId, String body, ...);
 *
 *   &#64;McpExclude
 *   String updateEntry(String corpusName, String entryId, String body, ...);
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpAutoRoute {
    String check();
    String alternate();
}
