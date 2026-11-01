package io.casehub.platform.registration.jpa;

import io.casehub.platform.registration.InvitationRecord;
import io.casehub.platform.registration.InvitationStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class JpaInvitationStore implements InvitationStore {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public InvitationRecord store(InvitationRecord invitation) {
        em.persist(InvitationEntity.fromRecord(invitation));
        return invitation;
    }

    @Override
    public Optional<InvitationRecord> findByTokenHash(String tokenHash) {
        return em.createQuery(
                        "SELECT e FROM InvitationEntity e WHERE e.tokenHash = :hash",
                        InvitationEntity.class)
                .setParameter("hash", tokenHash)
                .getResultStream()
                .findFirst()
                .map(InvitationEntity::toRecord);
    }

    @Override
    public Optional<InvitationRecord> findById(String id) {
        InvitationEntity entity = em.find(InvitationEntity.class, id);
        return entity != null ? Optional.of(entity.toRecord()) : Optional.empty();
    }

    @Override
    public Optional<InvitationRecord> findByEmail(String email, String tenancyId) {
        return em.createQuery(
                        "SELECT e FROM InvitationEntity e WHERE e.email = :email AND e.tenancyId = :tenancyId ORDER BY e.createdAt DESC",
                        InvitationEntity.class)
                .setParameter("email", email)
                .setParameter("tenancyId", tenancyId)
                .getResultStream()
                .findFirst()
                .map(InvitationEntity::toRecord);
    }

    @Override
    @Transactional
    public void consume(String tokenHash) {
        em.createQuery("UPDATE InvitationEntity e SET e.consumed = true WHERE e.tokenHash = :hash")
                .setParameter("hash", tokenHash)
                .executeUpdate();
    }

    @Override
    public List<InvitationRecord> findByTenancy(String tenancyId) {
        return em.createQuery(
                        "SELECT e FROM InvitationEntity e WHERE e.tenancyId = :tenancyId ORDER BY e.createdAt DESC",
                        InvitationEntity.class)
                .setParameter("tenancyId", tenancyId)
                .getResultList()
                .stream()
                .map(InvitationEntity::toRecord)
                .toList();
    }

    @Override
    @Transactional
    public void delete(String id) {
        em.createQuery("DELETE FROM InvitationEntity e WHERE e.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
