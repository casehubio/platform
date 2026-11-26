package io.casehub.platform.agent.guardrail.spring;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.guardrail.GuardrailException;
import io.casehub.platform.agent.guardrail.GuardrailResult;
import io.casehub.platform.agent.guardrail.GuardrailStreamOperator;
import io.casehub.platform.agent.guardrail.InputGuardrail;
import io.casehub.platform.agent.guardrail.OutputGuardrail;
import io.smallrye.mutiny.Multi;

import java.util.List;

public class GuardrailAgentProviderWrapper implements AgentProvider {

    private final AgentProvider delegate;
    private final List<InputGuardrail> inputGuardrails;
    private final List<OutputGuardrail> outputGuardrails;
    private final int maxReprompts;

    public GuardrailAgentProviderWrapper(AgentProvider delegate,
                                          List<InputGuardrail> inputGuardrails,
                                          List<OutputGuardrail> outputGuardrails,
                                          int maxReprompts) {
        this.delegate = delegate;
        this.inputGuardrails = inputGuardrails;
        this.outputGuardrails = outputGuardrails;
        this.maxReprompts = maxReprompts;
    }

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        for (InputGuardrail g : inputGuardrails) {
            GuardrailResult result = g.validate(config);
            if (result instanceof GuardrailResult.Fail f) {
                return Multi.createFrom().failure(new GuardrailException(f.reason()));
            }
            if (result instanceof GuardrailResult.Reprompt r) {
                return Multi.createFrom().failure(
                    new GuardrailException("Input reprompt not supported: " + r.repromptMessage()));
            }
        }

        Multi<AgentEvent> stream = delegate.invoke(config);
        if (outputGuardrails.isEmpty()) {
            return stream;
        }
        return GuardrailStreamOperator.validate(
            stream, outputGuardrails, config, maxReprompts,
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
