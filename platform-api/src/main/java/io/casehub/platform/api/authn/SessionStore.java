package io.casehub.platform.api.authn;

import java.util.Optional;

public interface SessionStore {
    void store(SessionRecord session);
    Optional<SessionRecord> findById(String sessionId);
    void delete(String sessionId);
    void deleteByActorId(String actorId, String tenancyId);
}
