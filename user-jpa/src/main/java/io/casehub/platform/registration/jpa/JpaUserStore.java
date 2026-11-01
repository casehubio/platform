package io.casehub.platform.registration.jpa;

import io.casehub.platform.registration.UserRecord;
import io.casehub.platform.registration.UserStatus;
import io.casehub.platform.registration.UserStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class JpaUserStore implements UserStore {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public UserRecord create(UserRecord user) {
        em.persist(UserEntity.fromRecord(user));
        return user;
    }

    @Override
    public Optional<UserRecord> findById(String id, String tenancyId) {
        UserEntity entity = em.find(UserEntity.class, id);
        if (entity == null || !entity.tenancyId.equals(tenancyId)) {
            return Optional.empty();
        }
        return Optional.of(entity.toRecord());
    }

    @Override
    public Optional<UserRecord> findByEmail(String email, String tenancyId) {
        return em.createQuery(
                        "SELECT e FROM UserEntity e WHERE e.email = :email AND e.tenancyId = :tenancyId",
                        UserEntity.class)
                .setParameter("email", email)
                .setParameter("tenancyId", tenancyId)
                .getResultStream()
                .findFirst()
                .map(UserEntity::toRecord);
    }

    @Override
    @Transactional
    public UserRecord updateStatus(String id, String tenancyId, UserStatus newStatus) {
        UserEntity entity = em.find(UserEntity.class, id);
        if (entity == null || !entity.tenancyId.equals(tenancyId)) {
            throw new IllegalArgumentException("User not found: " + id);
        }
        entity.status = newStatus.name();
        if (newStatus == UserStatus.ACTIVE && entity.activatedAt == null) {
            entity.activatedAt = Instant.now();
        }
        em.merge(entity);
        return entity.toRecord();
    }

    @Override
    @Transactional
    public void delete(String id, String tenancyId) {
        em.createQuery("DELETE FROM UserEntity e WHERE e.id = :id AND e.tenancyId = :tenancyId")
                .setParameter("id", id)
                .setParameter("tenancyId", tenancyId)
                .executeUpdate();
    }
}
