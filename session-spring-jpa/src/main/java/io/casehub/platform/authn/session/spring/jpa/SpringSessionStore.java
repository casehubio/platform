package io.casehub.platform.authn.session.spring.jpa;

import io.casehub.platform.api.authn.SessionRecord;
import io.casehub.platform.api.authn.SessionStore;
import io.casehub.platform.authn.session.jpa.SessionEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public class SpringSessionStore implements SessionStore {

    private final SessionRepository repo;

    public SpringSessionStore(SessionRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void store(SessionRecord session) {
        repo.save(SessionEntity.fromRecord(session));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionRecord> findById(String sessionId) {
        return repo.findById(sessionId).map(SessionEntity::toRecord);
    }

    @Override
    @Transactional
    public void delete(String sessionId) {
        repo.deleteById(sessionId);
    }

    @Override
    @Transactional
    public void deleteByActorId(String actorId, String tenancyId) {
        repo.deleteByActorIdAndTenancyId(actorId, tenancyId);
    }
}
