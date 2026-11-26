package io.casehub.platform.agent.guardrail;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@Decorator
@Priority(1400)
public class InputGuardrailDecorator implements AgentProvider {

    @Inject @Delegate @Any AgentProvider delegate;
    @Inject Instance<InputGuardrail> guardrails;

    protected InputGuardrailDecorator() {}

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        for (InputGuardrail g : guardrails) {
            GuardrailResult result = g.validate(config);
            if (result instanceof GuardrailResult.Fail f) {
                return Multi.createFrom().failure(new GuardrailException(f.reason()));
            }
            if (result instanceof GuardrailResult.Reprompt r) {
                return Multi.createFrom().failure(
                        new GuardrailException("Input reprompt not supported: " + r.repromptMessage()));
            }
        }
        return delegate.invoke(config);
    }

    @Override
    public AgentSession openSession(AgentSessionInit init) {
        return delegate.openSession(init);
    }
}
