package io.casehub.platform.api.identity;

import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.*;

/**
 * CDI Qualifier for individual DID method resolvers.
 *
 * <p>Beans annotated {@code @DIDMethod} are discovered by the composite
 * resolver and iterated in priority order. Consumer code injects
 * the unqualified {@code DIDResolver} to get the composite.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({TYPE, METHOD, FIELD, PARAMETER})
public @interface DIDMethod {}
