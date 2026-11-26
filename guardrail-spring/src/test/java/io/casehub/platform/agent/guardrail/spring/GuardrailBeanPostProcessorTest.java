package io.casehub.platform.agent.guardrail.spring;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.guardrail.GuardrailException;
import io.casehub.platform.agent.guardrail.GuardrailResult;
import io.casehub.platform.agent.guardrail.InputGuardrail;
import io.casehub.platform.agent.guardrail.OutputGuardrail;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardrailBeanPostProcessorTest {

    @Test
    void wrapsAgentProviderWithGuardrails() {
        InputGuardrail pass = config -> new GuardrailResult.Pass();
        var processor = new GuardrailBeanPostProcessor(List.of(pass), List.of(), 3);
        var stub = new StubAgentProvider();

        Object result = processor.postProcessAfterInitialization(stub, "myProvider");

        assertThat(result).isInstanceOf(GuardrailAgentProviderWrapper.class);
    }

    @Test
    void doesNotWrapNonAgentProviderBeans() {
        var processor = new GuardrailBeanPostProcessor(List.of(), List.of(), 3);
        var bean = "not a provider";

        Object result = processor.postProcessAfterInitialization(bean, "myString");

        assertThat(result).isSameAs(bean);
    }

    @Test
    void doesNotDoubleWrap() {
        var processor = new GuardrailBeanPostProcessor(List.of(), List.of(), 3);
        var wrapper = new GuardrailAgentProviderWrapper(new StubAgentProvider(), List.of(), List.of(), 3);

        Object result = processor.postProcessAfterInitialization(wrapper, "myWrapper");

        assertThat(result).isSameAs(wrapper);
    }

    @Test
    void wrappedProviderBlocksOnInputGuardrailFail() {
        InputGuardrail block = config -> new GuardrailResult.Fail("blocked");
        var processor = new GuardrailBeanPostProcessor(List.of(block), List.of(), 3);

        var wrapped = (AgentProvider) processor.postProcessAfterInitialization(
            new StubAgentProvider(), "myProvider");

        assertThatThrownBy(() ->
            wrapped.invoke(AgentSessionConfig.of("sys", "user"))
                .collect().asList().await().indefinitely()
        ).isInstanceOf(GuardrailException.class)
         .hasMessage("blocked");
    }

    static class StubAgentProvider implements AgentProvider {
        @Override
        public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            return Multi.createFrom().item(new AgentEvent.TextDelta("hello"));
        }
        @Override
        public AgentSession openSession(AgentSessionInit init) {
            return null;
        }
    }
}
