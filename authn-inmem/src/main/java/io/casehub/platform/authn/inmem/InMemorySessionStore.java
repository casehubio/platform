package io.casehub.platform.authn.inmem;

import io.casehub.platform.api.authn.SessionRecord;
import io.casehub.platform.api.authn.SessionStore;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<String, SessionRecord> sessions = new ConcurrentHashMap<>();

    @Override
    public void store(SessionRecord session) {
        sessions.put(session.sessionId(), session);
    }

    @Override
    public Optional<SessionRecord> findById(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public void delete(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public void deleteByActorId(String actorId, String tenancyId) {
        sessions.entrySet().removeIf(e ->
                e.getValue().actorId().equals(actorId)
                        && e.getValue().tenancyId().equals(tenancyId));
    }

    public int size() {
        return sessions.size();
    }
}
