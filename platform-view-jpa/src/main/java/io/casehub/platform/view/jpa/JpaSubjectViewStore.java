package io.casehub.platform.view.jpa;

import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.platform.api.view.SubjectViewStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaSubjectViewStore implements SubjectViewStore {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public SubjectViewSpec save(SubjectViewSpec spec) {
        SubjectViewEntity entity = SubjectViewEntity.fromSpec(spec);
        if (entity.id != null && em.find(SubjectViewEntity.class, entity.id) != null) {
            entity = em.merge(entity);
        } else {
            em.persist(entity);
        }
        em.flush();
        return entity.toSpec();
    }

    @Override
    public Optional<SubjectViewSpec> findById(UUID id) {
        SubjectViewEntity entity = em.find(SubjectViewEntity.class, id);
        return entity != null ? Optional.of(entity.toSpec()) : Optional.empty();
    }

    @Override
    public List<SubjectViewSpec> findByTenancy(String tenancyId) {
        return em.createQuery(
                "SELECT e FROM SubjectViewEntity e WHERE e.tenancyId = :t",
                SubjectViewEntity.class)
            .setParameter("t", tenancyId)
            .getResultList()
            .stream()
            .map(SubjectViewEntity::toSpec)
            .toList();
    }

    @Override
    @Transactional
    public boolean delete(UUID id) {
        SubjectViewEntity entity = em.find(SubjectViewEntity.class, id);
        if (entity != null) {
            em.remove(entity);
            return true;
        }
        return false;
    }
}
