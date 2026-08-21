package io.casehub.platform.agent.gate;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class SessionRegistry {

    private final AtomicLong idCounter = new AtomicLong();
    private final ConcurrentHashMap<Long, TrackedSession> sessions = new ConcurrentHashMap<>();

    public record TrackedSession(long id, GatedAgentSession session, Instant createdAt) {}

    long nextId() {
        return idCounter.incrementAndGet();
    }

    long register(GatedAgentSession session) {
        long id = nextId();
        sessions.put(id, new TrackedSession(id, session, Instant.now()));
        return id;
    }

    void register(long id, GatedAgentSession session) {
        sessions.put(id, new TrackedSession(id, session, Instant.now()));
    }

    void registerWithTimestamp(long id, GatedAgentSession session, Instant createdAt) {
        sessions.put(id, new TrackedSession(id, session, createdAt));
    }

    void deregister(long id) {
        sessions.remove(id);
    }

    Map<Long, TrackedSession> snapshot() {
        return Map.copyOf(sessions);
    }
}
