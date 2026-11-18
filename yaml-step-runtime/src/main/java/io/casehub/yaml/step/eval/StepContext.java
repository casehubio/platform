package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.error.SourceLocation;
import io.casehub.yaml.core.resolver.VariableResolver;

import java.time.Duration;

public final class StepContext {

    private final VariableResolver resolver;
    private final DeadlineContext  deadline;
    private final SourceLocation   location;
    private final String           stepName;

    public StepContext(VariableResolver resolver) {
        this(resolver, DeadlineContext.NONE, SourceLocation.UNKNOWN, null);
    }

    public StepContext(VariableResolver resolver, DeadlineContext deadline) {
        this(resolver, deadline, SourceLocation.UNKNOWN, null);
    }

    public StepContext(VariableResolver resolver, DeadlineContext deadline, SourceLocation location) {
        this(resolver, deadline, location, null);
    }

    private StepContext(VariableResolver resolver, DeadlineContext deadline, SourceLocation location, String stepName) {
        this.resolver = resolver;
        this.deadline = deadline;
        this.location = location;
        this.stepName = stepName;
    }

    public VariableResolver resolver() {return resolver;}

    public DeadlineContext deadline()  {return deadline;}

    public SourceLocation location()   {return location;}

    public String stepName()           {return stepName;}

    public StepContext withResolver(VariableResolver resolver) {
        return new StepContext(resolver, this.deadline, this.location, this.stepName);
    }

    public StepContext withDeadline(Duration timeout) {
        return new StepContext(resolver, deadline.withTimeout(timeout), this.location, this.stepName);
    }

    public StepContext withLocation(SourceLocation location) {
        return new StepContext(resolver, deadline, location, this.stepName);
    }

    public StepContext withStepName(String stepName) {
        return new StepContext(resolver, deadline, location, stepName);
    }
}
