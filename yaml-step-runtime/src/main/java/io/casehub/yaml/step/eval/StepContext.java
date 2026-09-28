package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.resolver.VariableResolver;

import java.time.Duration;
import java.util.Optional;

public final class StepContext {

    private final VariableResolver resolver;
    private final DeadlineContext deadline;

    public StepContext(VariableResolver resolver) {
        this(resolver, DeadlineContext.NONE);
    }

    public StepContext(VariableResolver resolver, DeadlineContext deadline) {
        this.resolver = resolver;
        this.deadline = deadline;
    }

    public VariableResolver resolver() { return resolver; }

    public DeadlineContext deadline() { return deadline; }

    public StepContext withResolver(VariableResolver resolver) {
        return new StepContext(resolver, this.deadline);
    }

    public StepContext withDeadline(Duration timeout) {
        return new StepContext(resolver, deadline.withTimeout(timeout));
    }
}
