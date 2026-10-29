package io.casehub.platform.agent.http;

import io.casehub.platform.agent.AgentDescriptor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentInvocationResult;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentResolver;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.ResolvedAgent;
import io.casehub.platform.agent.SessionRegistry;
import io.casehub.platform.api.mcp.ComponentStatus;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentHttpServiceTest {

    private AgentProvider agentProvider;
    private AgentResolver agentResolver;
    private SessionRegistry sessionRegistry;
    private HttpSessionManager sessionManager;
    private AgentHttpService service;
    private AgentHttpService serviceNoSessions;

    @BeforeEach
    void setUp() {
        agentProvider = mock(AgentProvider.class);
        agentResolver = mock(AgentResolver.class);
        sessionRegistry = mock(SessionRegistry.class);
        sessionManager = new HttpSessionManager(sessionRegistry, agentProvider);
        service = new AgentHttpService(agentProvider, agentResolver, sessionManager);
        serviceNoSessions = new AgentHttpService(agentProvider, agentResolver, null);
    }

    @Test
    void invoke_delegatesToAgentProvider() {
        var config = AgentSessionConfig.of("system", "user");
        var expected = new AgentInvocationResult("response", 10, 20, 0, 0, 0, 0.01, 500L, "claude", "default");
        when(agentProvider.invokeBlocking(config, Duration.ofSeconds(120))).thenReturn(expected);

        var result = service.invoke(config, null);

        assertEquals("response", result.text());
        verify(agentProvider).invokeBlocking(config, Duration.ofSeconds(120));
    }

    @Test
    void invoke_resolvesAgentId() {
        var resolved = new ResolvedAgent("my-agent", "resolved prompt", "claude-opus",
                Duration.ofSeconds(60), Map.of());
        when(agentResolver.resolve("my-agent")).thenReturn(Optional.of(resolved));
        var config = AgentSessionConfig.of("ignored", "user prompt");
        when(agentProvider.invokeBlocking(any(), eq(Duration.ofSeconds(60))))
                .thenReturn(new AgentInvocationResult("ok", 0, 0, 0, 0, 0, null, 0L, null, null));

        service.invoke(config, "my-agent");

        var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invokeBlocking(captor.capture(), eq(Duration.ofSeconds(60)));
        assertEquals("resolved prompt", captor.getValue().systemPrompt());
        assertEquals("user prompt", captor.getValue().userPrompt());
    }

    @Test
    void invoke_unknownAgentThrows() {
        when(agentResolver.resolve("bad")).thenReturn(Optional.empty());
        var config = AgentSessionConfig.of("sys", "usr");
        assertThrows(IllegalArgumentException.class, () -> service.invoke(config, "bad"));
    }

    @Test
    void invokeStream_returnsMulti() {
        var config = AgentSessionConfig.of("system", "user");
        var event = new AgentEvent.TextDelta("hello");
        when(agentProvider.invoke(config)).thenReturn(Multi.createFrom().item(event));

        var result = service.invokeStream(config, null);
        var events = result.collect().asList().await().indefinitely();

        assertEquals(1, events.size());
        assertInstanceOf(AgentEvent.TextDelta.class, events.get(0));
    }

    @Test
    void openSession_returnsHandle() {
        var init = AgentSessionInit.of("system prompt");
        var mockSession = mock(AgentSession.class);
        when(sessionRegistry.openSession(anyString(), eq(init))).thenReturn(mockSession);

        var handle = service.openSession(init, null);

        assertNotNull(handle.sessionId());
        assertFalse(handle.sessionId().isEmpty());
        verify(sessionRegistry).openSession(anyString(), eq(init));
    }

    @Test
    void querySession_delegatesToSession() {
        var init = AgentSessionInit.of("prompt");
        var mockSession = mock(AgentSession.class);
        when(sessionRegistry.openSession(anyString(), eq(init))).thenReturn(mockSession);
        var handle = service.openSession(init, null);

        when(sessionRegistry.get(handle.sessionId())).thenReturn(Optional.of(mockSession));
        var event = new AgentEvent.TextDelta("response");
        when(mockSession.query("hello")).thenReturn(Multi.createFrom().item(event));

        var result = service.querySession(handle.sessionId(), "hello");
        var events = result.collect().asList().await().indefinitely();

        assertEquals(1, events.size());
    }

    @Test
    void querySession_throwsForUnknownSession() {
        when(sessionRegistry.get("unknown")).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.querySession("unknown", "hello"));
    }

    @Test
    void sessionEndpoints_throw501WhenNoSessionManager() {
        var init = AgentSessionInit.of("prompt");
        assertThrows(UnsupportedOperationException.class, () -> serviceNoSessions.openSession(init, null));
        assertThrows(UnsupportedOperationException.class, () -> serviceNoSessions.querySession("id", "q"));
        assertThrows(UnsupportedOperationException.class, () -> serviceNoSessions.interruptSession("id"));
        assertThrows(UnsupportedOperationException.class, () -> serviceNoSessions.clearSession("id"));
        assertThrows(UnsupportedOperationException.class, () -> serviceNoSessions.closeSession("id"));
    }

    @Test
    void listAgents_delegatesToResolver() {
        var agents = List.of(new AgentDescriptor("a1", "Agent 1", "desc", Map.of()));
        when(agentResolver.discover()).thenReturn(agents);

        assertEquals(agents, service.listAgents());
    }

    @Test
    void listAgents_returnsEmptyWhenNoResolver() {
        var noResolver = new AgentHttpService(agentProvider, null, null);
        assertEquals(List.of(), noResolver.listAgents());
    }

    @Test
    void status_reportsAvailableCapabilities() {
        var status = service.status();

        assertTrue(status.availableCapabilities().contains("invoke"));
        assertTrue(status.availableCapabilities().contains("sessions"));
        assertEquals(ComponentStatus.AVAILABLE, status.overallStatus());
    }

    @Test
    void status_omitsSessionsWhenNoManager() {
        var status = serviceNoSessions.status();

        assertTrue(status.availableCapabilities().contains("invoke"));
        assertFalse(status.availableCapabilities().contains("sessions"));
    }

    @Test
    void closeSession_releasesFromRegistry() {
        var init = AgentSessionInit.of("prompt");
        var mockSession = mock(AgentSession.class);
        when(sessionRegistry.openSession(anyString(), eq(init))).thenReturn(mockSession);
        var handle = service.openSession(init, null);

        when(sessionRegistry.get(handle.sessionId())).thenReturn(Optional.of(mockSession));

        service.closeSession(handle.sessionId());

        verify(mockSession).close(any(Duration.class));
        verify(sessionRegistry).releaseSession(handle.sessionId());
    }
}
