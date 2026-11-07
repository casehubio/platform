package io.casehub.platform.api.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as MCP-only — it appears in the MCP tool surface
 * but is not projected to REST or GraphQL. The method must also
 * carry {@link McpDelegate} to specify which SPI method to invoke.
 *
 * <p>Use for agent-facing tools that compose or reshape SPI
 * operations in ways that don't map to a single REST endpoint.
 *
 * <p>The method body on the interface is never called — the
 * dispatcher uses {@link McpDelegate} to route the invocation.
 * Declare as a {@code default} method with an
 * {@code UnsupportedOperationException} body, or leave abstract
 * if the interface has no other implementors.
 *
 * <p>Example:
 * <pre>
 *   &#64;McpOnly("What changed since a point in time")
 *   &#64;McpDelegate(spi = ChangeSource.class, method = "changesSince")
 *   default List&lt;ChangeRecord&gt; changes(String corpusName, Instant since) {
 *       throw new UnsupportedOperationException("MCP-only");
 *   }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpOnly {
    String value();
}
