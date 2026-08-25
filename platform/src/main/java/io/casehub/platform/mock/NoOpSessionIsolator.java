package io.casehub.platform.mock;

import io.casehub.platform.api.governance.SessionIsolator;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.function.Supplier;

@ApplicationScoped
@DefaultBean
public class NoOpSessionIsolator implements SessionIsolator {

    @Override
    public <T> T runIsolated(Supplier<T> work) {
        return work.get();
    }
}
