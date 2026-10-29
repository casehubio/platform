package io.casehub.platform.agent.http;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.SessionHandle;
import io.casehub.platform.agent.SessionInfo;
import io.casehub.platform.agent.SessionRegistry;
import io.casehub.platform.api.util.UUIDv7;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class HttpSessionManager {

    private final SessionRegistry sessionRegistry;
    private final AgentProvider agentProvider;
    private final ConcurrentHashMap<String, SessionMetadata> metadata = new ConcurrentHashMap<>();

    public HttpSessionManager(SessionRegistry sessionRegistry, AgentProvider agentProvider) {
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry);
        this.agentProvider = Objects.requireNonNull(agentProvider);
    }

    public SessionHandle open(AgentSessionInit init) {
        String id = UUIDv7.generate().toString();
        sessionRegistry.openSession(id, init);
        int     timeoutSec = init.timeout() != null ? (int) init.timeout().toSeconds() : 120;
        Instant now        = Instant.now();
        metadata.put(id, new SessionMetadata(now, timeoutSec));
        return new SessionHandle(id, "", now, timeoutSec);
    }

    public AgentSession get(String sessionId) {
        return sessionRegistry.get(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
    }

    public void release(String sessionId) {
        sessionRegistry.releaseSession(sessionId);
        metadata.remove(sessionId);
    }

    public List<SessionInfo> listSessions() {
        return metadata.entrySet().stream()
                .map(e -> new SessionInfo(e.getKey(), "", e.getValue().createdAt(), 0, e.getValue().createdAt()))
                .toList();
    }

    public int activeCount() {
        return metadata.size();
    }

    record SessionMetadata(Instant createdAt, int timeoutSeconds) {}
}
