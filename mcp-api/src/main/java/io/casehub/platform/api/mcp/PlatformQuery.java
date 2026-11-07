package io.casehub.platform.api.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlatformQuery {
    String value() default "";

    /**
     * When {@code true}, the MCP dispatcher records access via
     * {@link ReadTracker} after the method completes. Enables
     * "what haven't I read?" queries for agents maintaining
     * awareness over a resource. Defaults to {@code false}.
     *
     * <p>Only affects the MCP surface — REST and GraphQL
     * projections do not record access.
     */
    boolean track() default false;
}
