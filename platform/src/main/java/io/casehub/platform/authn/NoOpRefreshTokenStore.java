package io.casehub.platform.authn;

import io.casehub.platform.api.authn.RefreshTokenRecord;
import io.casehub.platform.api.authn.RefreshTokenStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpRefreshTokenStore implements RefreshTokenStore {

    @Override
    public void store(RefreshTokenRecord record) {}

    @Override
    public Optional<RefreshTokenRecord> findByToken(String token) {
        return Optional.empty();
    }

    @Override
    public void consume(String token) {}

    @Override
    public void revokeFamily(String familyId) {}

    @Override
    public void revokeByActorId(String actorId, String tenancyId) {}

    @Override
    public int purgeExpired(Instant before) {
        return 0;
    }
}
