package io.casehub.platform.agent.gate.spring;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.gate.GatedAgentProviderWrapper;
import io.casehub.platform.agent.gate.SessionRegistry;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AgentGateBeanPostProcessorTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AgentGateSpringAutoConfiguration.class));

    @Test
    void wrapsAgentProviderBean() {
        runner.withBean("testProvider", AgentProvider.class, StubAgentProvider::new)
                .run(context -> {
                    AgentProvider provider = context.getBean(AgentProvider.class);
                    assertThat(provider).isInstanceOf(GatedAgentProviderWrapper.class);
                });
    }

    @Test
    void doesNotWrapNonAgentProviderBean() {
        runner.withBean("testProvider", AgentProvider.class, StubAgentProvider::new)
                .withBean("someOther", String.class, () -> "hello")
                .run(context -> {
                    String other = context.getBean(String.class);
                    assertThat(other).isEqualTo("hello");
                });
    }

    @Test
    void doesNotDoubleWrapAlreadyWrappedProvider() {
        runner.withBean("testProvider", AgentProvider.class, StubAgentProvider::new)
                .run(context -> {
                    var beans = context.getBeansOfType(AgentProvider.class);
                    assertThat(beans).hasSize(1);
                    assertThat(beans.values().iterator().next())
                            .isInstanceOf(GatedAgentProviderWrapper.class);
                });
    }

    @Test
    void sessionRegistryBeanExists() {
        runner.withBean("testProvider", AgentProvider.class, StubAgentProvider::new)
                .run(context ->
                    assertThat(context.getBean("agentGateSessionRegistry")).isNotNull());
    }

    @Test
    void sessionLeakReaperBeanExists() {
        runner.withBean("testProvider", AgentProvider.class, StubAgentProvider::new)
                .run(context ->
                    assertThat(context.getBean(
                            io.casehub.platform.agent.gate.SessionLeakReaper.class))
                            .isNotNull());
    }

    @Test
    void processorOrderIs2000() {
        var processor = new AgentGateBeanPostProcessor(
                new AgentGateSpringProperties(null, null, null, null, null, null),
                new SessionRegistry());
        assertThat(processor.getOrder()).isEqualTo(2000);
    }

    static class StubAgentProvider implements AgentProvider {
        @Override
        public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            return Multi.createFrom().item(new AgentEvent.TextDelta("stub"));
        }

        @Override
        public AgentSession openSession(AgentSessionInit init) {
            throw new UnsupportedOperationException();
        }
    }
}
