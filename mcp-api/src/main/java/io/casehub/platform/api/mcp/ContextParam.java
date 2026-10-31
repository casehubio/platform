package io.casehub.platform.api.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter as resolved from the request context rather than
 * from the client. Generated REST and GraphQL endpoints skip this
 * parameter in their signatures and resolve it server-side.
 *
 * <p>Built-in context keys:
 * <ul>
 *   <li>{@code "tenancyId"} — resolved from {@code CurrentPrincipal.tenancyId()}</li>
 *   <li>{@code "actorId"} — resolved from {@code CurrentPrincipal.actorId()}</li>
 * </ul>
 *
 * <p>Internal/system callers using the SPI interface directly still
 * pass the value explicitly as a method parameter.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ContextParam {
    String value();
}
