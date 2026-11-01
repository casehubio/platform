package io.casehub.platform.registration.spring.jpa;

import io.casehub.platform.registration.UserRecord;
import io.casehub.platform.registration.UserStatus;
import io.casehub.platform.registration.UserStore;
import io.casehub.platform.registration.jpa.UserEntity;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public class SpringUserStore implements UserStore {

    private final UserEntityRepository repo;

    public SpringUserStore(UserEntityRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public UserRecord create(UserRecord user) {
        repo.save(UserEntity.fromRecord(user));
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserRecord> findById(String id, String tenancyId) {
        return repo.findById(id)
                .filter(e -> e.tenancyId.equals(tenancyId))
                .map(UserEntity::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserRecord> findByEmail(String email, String tenancyId) {
        return repo.findByEmailAndTenancyId(email, tenancyId).map(UserEntity::toRecord);
    }

    @Override
    @Transactional
    public UserRecord updateStatus(String id, String tenancyId, UserStatus newStatus) {
        UserEntity entity = repo.findById(id)
                .filter(e -> e.tenancyId.equals(tenancyId))
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        entity.status = newStatus.name();
        if (newStatus == UserStatus.ACTIVE && entity.activatedAt == null) {
            entity.activatedAt = Instant.now();
        }
        repo.save(entity);
        return entity.toRecord();
    }

    @Override
    @Transactional
    public void delete(String id, String tenancyId) {
        repo.deleteByIdAndTenancyId(id, tenancyId);
    }
}
