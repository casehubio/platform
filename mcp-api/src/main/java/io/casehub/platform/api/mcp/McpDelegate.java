package io.casehub.platform.api.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies which SPI method to invoke for an {@link McpOnly} tool.
 * The dispatcher resolves the SPI bean from the application context
 * and invokes the named method with parameters matched by name.
 *
 * <p>When used with {@link McpCursorMap}, parameter type conversion
 * is applied before invocation (e.g. ISO-8601 timestamp to internal
 * cursor format).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpDelegate {
    Class<?> spi();
    String method();
}
