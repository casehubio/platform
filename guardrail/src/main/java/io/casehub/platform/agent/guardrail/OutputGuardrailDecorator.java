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
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@Decorator
@Priority(1600)
public class OutputGuardrailDecorator implements AgentProvider {

    @Inject @Delegate @Any AgentProvider delegate;
    @Inject Instance<OutputGuardrail> guardrails;

    @ConfigProperty(name = "casehub.guardrail.output.max-reprompts", defaultValue = "3")
    int maxReprompts;

    protected OutputGuardrailDecorator() {}

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        Multi<AgentEvent> stream = delegate.invoke(config);
        if (guardrails.isUnsatisfied()) {
            return stream;
        }
        List<OutputGuardrail> sorted = guardrails.stream().toList();
        return GuardrailStreamOperator.validate(
            stream, sorted, config, maxReprompts,
            reprompt -> delegate.invoke(AgentSessionConfig.of(config.systemPrompt(), reprompt)
                .withModel(config.model())
                .withResponseFormat(config.responseFormat())
                .withTools(config.tools()))
        );
    }

    @Override
    public AgentSession openSession(AgentSessionInit init) {
        return delegate.openSession(init);
    }
}
