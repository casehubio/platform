package io.casehub.platform.api.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controls how a parameter is projected to the MCP tool surface.
 * The Java method signature and REST/GraphQL projections are unchanged.
 *
 * <p>Example: a {@code String metadata} parameter that the bean parses
 * as pipe-separated key=value pairs can be declared as:
 * <pre>
 *   &#64;McpParam(format = McpParamFormat.MAP)
 *   String metadata
 * </pre>
 * The MCP tool schema will accept a JSON object. The dispatcher
 * serialises the JSON object to the format the bean expects before
 * invoking the method.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpParam {
    McpParamFormat format();
}
