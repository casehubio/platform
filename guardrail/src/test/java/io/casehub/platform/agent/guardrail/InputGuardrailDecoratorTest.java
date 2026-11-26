package io.casehub.platform.agent.guardrail;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class InputGuardrailDecoratorTest {

    @Inject
    AgentProvider provider;

    @Test
    void passesWhenGuardrailPasses() {
        ToggleGuardrail.block = false;
        var result = provider.invoke(AgentSessionConfig.of("sys", "user"))
            .collect().asList().await().indefinitely();
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(AgentEvent.TextDelta.class);
    }

    @Test
    void failsWhenGuardrailBlocks() {
        ToggleGuardrail.block = true;
        assertThatThrownBy(() ->
            provider.invoke(AgentSessionConfig.of("sys", "blocked"))
                .collect().asList().await().indefinitely()
        ).isInstanceOf(GuardrailException.class)
         .hasMessage("blocked");
    }

    @Test
    void openSessionDelegatesToProvider() {
        ToggleGuardrail.block = false;
        var session = provider.openSession(AgentSessionInit.of("sys"));
        assertThat(session).isNull();
    }

    @ApplicationScoped
    public static class StubAgentProvider implements AgentProvider {
        @Override
        public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            return Multi.createFrom().item(new AgentEvent.TextDelta("hello"));
        }
        @Override
        public AgentSession openSession(AgentSessionInit init) {
            return null;
        }
    }

    @ApplicationScoped
    public static class ToggleGuardrail implements InputGuardrail {
        static volatile boolean block = false;
        @Override
        public GuardrailResult validate(AgentSessionConfig config) {
            if (block) return new GuardrailResult.Fail("blocked");
            return new GuardrailResult.Pass();
        }
    }
}
