package io.casehub.platform.view.jpa;

import io.casehub.platform.api.view.CrossTenantSubjectViewStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;

@ApplicationScoped
public class JpaCrossTenantSubjectViewStore implements CrossTenantSubjectViewStore {

    @Inject
    EntityManager em;

    @Override
    public List<String> findDistinctTenancyIds() {
        return em.createQuery(
                "SELECT DISTINCT e.tenancyId FROM SubjectViewEntity e",
                String.class)
            .getResultList();
    }
}
