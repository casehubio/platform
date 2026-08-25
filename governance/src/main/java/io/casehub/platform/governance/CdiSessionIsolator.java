package io.casehub.platform.governance;

import io.casehub.platform.api.governance.SessionIsolator;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableContext.ContextState;
import io.quarkus.arc.ManagedContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.function.Supplier;

@ApplicationScoped
public class CdiSessionIsolator implements SessionIsolator {

    @Override
    public <T> T runIsolated(Supplier<T> work) {
        ManagedContext requestContext = Arc.container().requestContext();
        boolean wasActive = requestContext.isActive();
        ContextState previous = wasActive ? requestContext.getState() : null;

        if (wasActive) {
            requestContext.deactivate();
        }

        requestContext.activate();
        try {
            return work.get();
        } finally {
            requestContext.terminate();
            if (previous != null) {
                requestContext.activate(previous);
            }
        }
    }
}
