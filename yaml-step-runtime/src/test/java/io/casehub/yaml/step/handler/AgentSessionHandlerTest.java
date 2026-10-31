package io.casehub.yaml.step.handler;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.config.PoolDeclaration;
import io.casehub.platform.agent.session.ClearingPolicy;
import io.casehub.platform.agent.session.SessionLifecycleManager;
import io.casehub.platform.agent.session.SessionPipeline;
import io.casehub.platform.agent.session.SessionPoolConfig;
import io.casehub.platform.agent.session.SessionPoolRegistry;
import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.plugin.api.Result;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSessionHandlerTest {

    private SessionPipeline pipeline;

    private SessionPoolRegistry poolRegistry;

    @BeforeEach
    void setUp() {
        var backend = new StubBackend();
        var backends = new StubBackendInstanceRegistry(backend);
        var decl = new PoolDeclaration("test-pool", "test", "stub",
                0, 5, null, Map.of(), Map.of());
        poolRegistry = new SessionPoolRegistry(List.of(decl), backends);
        var manager = new SessionLifecycleManager(poolRegistry);
        pipeline = new SessionPipeline(manager, "stub", 5, 8000);
    }

    @Test
    void sessionOpen_createsNamedSession() {
        var binding = new InvokeBinding.SessionOpen(
                "analyst", "You are a data analyst", null,
                null, null, null, "60s", null);
        var handler = new AgentSessionOpenHandler(pipeline);
        assertThat(handler.supports(binding)).isTrue();
        var action = handler.create(null, binding);
        var result = action.execute(Map.of(), null);
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(pipeline.sessionFor("analyst")).isNotNull();
    }

    @Test
    void sessionQuery_queriesNamedSession() {
        pipeline.openSession("analyst",
                AgentSessionInit.of("You are an analyst"),
                ClearingPolicy.MANUAL);
        var binding = new InvokeBinding.SessionQuery(
                "analyst", "What is 2+2?", null, null, false);
        var handler = new AgentSessionQueryHandler(pipeline);
        assertThat(handler.supports(binding)).isTrue();
        var action = handler.create(null, binding);
        var result = action.execute(Map.of(), null);
        assertThat(result).isInstanceOf(Result.Success.class);
    }

    @Test
    void sessionClear_clearsContext() {
        pipeline.openSession("analyst",
                AgentSessionInit.of("You are an analyst"),
                ClearingPolicy.MANUAL);
        var binding = new InvokeBinding.SessionClear("analyst");
        var handler = new AgentSessionClearHandler(pipeline);
        assertThat(handler.supports(binding)).isTrue();
        var action = handler.create(null, binding);
        var result = action.execute(Map.of(), null);
        assertThat(result).isInstanceOf(Result.Success.class);
    }

    @Test
    void sessionClose_releasesSession() {
        pipeline.openSession("analyst",
                AgentSessionInit.of("You are an analyst"),
                ClearingPolicy.MANUAL);
        assertThat(pipeline.sessionFor("analyst")).isNotNull();
        var binding = new InvokeBinding.SessionClose("analyst");
        var handler = new AgentSessionCloseHandler(pipeline);
        assertThat(handler.supports(binding)).isTrue();
        var action = handler.create(null, binding);
        var result = action.execute(Map.of(), null);
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(pipeline.sessionFor("analyst")).isNull();
    }

    @Test
    void handlersRejectWrongBindingType() {
        var agentBinding = new InvokeBinding.Mcp("test");
        assertThat(new AgentSessionOpenHandler(pipeline).supports(agentBinding)).isFalse();
        assertThat(new AgentSessionQueryHandler(pipeline).supports(agentBinding)).isFalse();
        assertThat(new AgentSessionClearHandler(pipeline).supports(agentBinding)).isFalse();
        assertThat(new AgentSessionCloseHandler(pipeline).supports(agentBinding)).isFalse();
    }

    private static class StubBackend implements AgentBackend {
        @Override public String key() { return "stub"; }
        @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            return Multi.createFrom().item(new AgentEvent.TextDelta("stub response"));
        }
        @Override public AgentSession openSession(AgentSessionInit init) {
            return new StubSession();
        }
    }

    private static class StubSession implements AgentSession {
        @Override public Multi<AgentEvent> query(String prompt) {
            return Multi.createFrom().item(new AgentEvent.TextDelta("stub: " + prompt));
        }
        @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
        @Override public void clear() {}
        @Override public void close(Duration maxWait) {}
        @Override public void close() {}
    }

    private record StubBackendInstanceRegistry(AgentBackend backend) implements BackendInstanceRegistry {
        @Override public void register(AgentBackend b) {}
        @Override public Optional<AgentBackend> resolve(String key, String instanceId) {
            return "stub".equals(key) ? Optional.of(backend) : Optional.empty();
        }
        @Override public List<AgentBackend> resolveByKey(String key) {
            return "stub".equals(key) ? List.of(backend) : List.of();
        }
    }
}
