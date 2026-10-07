package io.casehub.platform.authn.session.spring.jpa;

import io.casehub.platform.api.authn.RefreshTokenRecord;
import io.casehub.platform.api.authn.RefreshTokenStore;
import io.casehub.platform.authn.session.jpa.RefreshTokenEntity;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public class SpringRefreshTokenStore implements RefreshTokenStore {

    private final RefreshTokenRepository repo;

    public SpringRefreshTokenStore(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void store(RefreshTokenRecord record) {
        repo.save(RefreshTokenEntity.fromRecord(record));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshTokenRecord> findByToken(String token) {
        return repo.findById(token).map(RefreshTokenEntity::toRecord);
    }

    @Override
    @Transactional
    public void consume(String token) {
        repo.findById(token).ifPresent(e -> {
            e.consumed = true;
            repo.save(e);
        });
    }

    @Override
    @Transactional
    public void revokeFamily(String familyId) {
        repo.deleteByFamilyId(familyId);
    }

    @Override
    @Transactional
    public void revokeByActorId(String actorId, String tenancyId) {
        repo.deleteByActorIdAndTenancyId(actorId, tenancyId);
    }

    @Override
    @Transactional
    public int purgeExpired(Instant before) {
        return repo.purgeExpired(before);
    }
}
