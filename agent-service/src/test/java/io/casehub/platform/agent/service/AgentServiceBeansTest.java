package io.casehub.platform.agent.service;

import io.casehub.platform.agent.AgentDescriptor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentResolver;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.ResolvedAgent;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class AgentServiceBeansTest {

    @Inject AgentServiceBeans agentServiceBeans;

    @ApplicationScoped
    @Produces
    TestCoach testCoach(AgentServiceBeans beans) {
        return beans.createProxy(TestCoach.class);
    }

    @Inject TestCoach coach;

    @Test
    void proxyIsInjectable() {
        assertThat(coach).isNotNull();
    }

    @Test
    void proxyDelegatesToAgentProvider() {
        var result = coach.decompose("learn Java");
        assertThat(result).isEqualTo("stub response");
    }

    @ApplicationScoped
    public static class StubProvider implements AgentProvider {
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

    @ApplicationScoped
    public static class StubResolver implements AgentResolver {
        @Override
        public Optional<ResolvedAgent> resolve(String agentId) {
            return Optional.of(new ResolvedAgent(agentId, "You are a coach.", null, null, null));
        }
        @Override
        public List<AgentDescriptor> discover() {
            return List.of();
        }
    }
}
