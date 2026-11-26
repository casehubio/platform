package io.casehub.platform.agent.service.spring;

import io.casehub.platform.agent.AgentDescriptor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentResolver;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.ResolvedAgent;
import io.casehub.platform.agent.service.AgentServiceBeans;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AgentServiceAutoConfigurationTest {

    @AutoConfigurationPackage
    static class TestConfig {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentServiceAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class)
            .withBean(AgentProvider.class, StubAgentProvider::new)
            .withBean(AgentResolver.class, StubAgentResolver::new);

    @Test
    void discoversAndRegistersAgentServiceProxy() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TestAdvisor.class);
            TestAdvisor advisor = context.getBean(TestAdvisor.class);
            assertThat(advisor.advise("Java")).isEqualTo("stub response");
        });
    }

    @Test
    void registersAgentServiceBeansBean() {
        runner.run(context ->
            assertThat(context).hasSingleBean(AgentServiceBeans.class));
    }

    @Test
    void skipsWhenNoAutoConfigurationPackages() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AgentServiceAutoConfiguration.class))
                .withBean(AgentProvider.class, StubAgentProvider::new)
                .withBean(AgentResolver.class, StubAgentResolver::new)
                .run(context ->
                    assertThat(context).doesNotHaveBean(TestAdvisor.class));
    }

    static class StubAgentProvider implements AgentProvider {
        @Override
        public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            return Multi.createFrom().items(
                new AgentEvent.TextDelta("stub response"),
                new AgentEvent.InvocationComplete(5, 3, 0, 0, 0, 0.001, 50, 40, null, 1, false, "stub", null)
            );
        }
        @Override
        public AgentSession openSession(AgentSessionInit init) {
            return null;
        }
    }

    static class StubAgentResolver implements AgentResolver {
        @Override
        public Optional<ResolvedAgent> resolve(String agentId) {
            return Optional.of(new ResolvedAgent(agentId, "You are an advisor.", null, null, null));
        }
        @Override
        public List<AgentDescriptor> discover() {
            return List.of();
        }
    }
}
