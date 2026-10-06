package io.casehub.platform.authn;

import io.casehub.platform.api.authn.SessionRecord;
import io.casehub.platform.api.authn.SessionStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpSessionStore implements SessionStore {

    @Override
    public void store(SessionRecord session) {}

    @Override
    public Optional<SessionRecord> findById(String sessionId) {
        return Optional.empty();
    }

    @Override
    public void delete(String sessionId) {}

    @Override
    public void deleteByActorId(String actorId, String tenancyId) {}
}
