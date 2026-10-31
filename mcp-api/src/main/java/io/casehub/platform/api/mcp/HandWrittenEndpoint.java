package io.casehub.platform.api.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code @Path}-annotated REST resource as intentionally hand-written,
 * exempting it from the {@code @McpDomain} enforcement build step.
 *
 * <p>Use this for endpoints that cannot go through the unified generation pipeline:
 * inbound webhooks, SSE streaming, authentication flows, or simulation-only resources.
 *
 * <p>The {@link #value()} must explain why this endpoint stays hand-written.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HandWrittenEndpoint {
    /** Reason this endpoint is hand-written rather than @McpDomain-generated. */
    String value();
}
