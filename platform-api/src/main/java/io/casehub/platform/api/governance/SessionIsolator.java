package io.casehub.platform.api.governance;

import java.util.function.Supplier;

/**
 * Runs work in an isolated Hibernate session, preventing corruption when
 * multiple virtual threads share the same CDI request context.
 */
public interface SessionIsolator {

    <T> T runIsolated(Supplier<T> work);

    default void runIsolated(Runnable work) {
        runIsolated(() -> {
            work.run();
            return null;
        });
    }
}
