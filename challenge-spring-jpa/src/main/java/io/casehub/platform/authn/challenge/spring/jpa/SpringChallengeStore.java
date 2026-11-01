package io.casehub.platform.authn.challenge.spring.jpa;

import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.ChallengeStore;
import io.casehub.platform.authn.challenge.jpa.ChallengeEntity;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public class SpringChallengeStore implements ChallengeStore {

    private final ChallengeEntityRepository repo;

    public SpringChallengeStore(ChallengeEntityRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void store(ChallengeRecord record) {
        repo.save(ChallengeEntity.fromRecord(record));
    }

    @Override
    @Transactional
    public Optional<ChallengeRecord> consume(String challengeId) {
        return repo.findById(challengeId).map(entity -> {
            ChallengeRecord record = entity.toRecord();
            repo.delete(entity);
            return record;
        });
    }

    @Transactional
    public int purgeExpired() {
        return repo.deleteExpiredBefore(Instant.now());
    }
}
