package io.casehub.platform.api.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes a method from the MCP tool surface. The method still
 * projects to REST and GraphQL as normal.
 *
 * <p>Use when a method is too fine-grained for LLM agents (e.g.
 * paired add/remove operations replaced by a single upsert tool)
 * or when the method is consumed internally by another MCP
 * annotation such as {@link McpUpsert}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpExclude {
}
