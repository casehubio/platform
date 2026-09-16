package io.casehub.platform.acl.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TestDataCleaner {

    @Inject EntityManager entityManager;

    @Transactional
    public void deleteAll() {
        entityManager.createQuery("delete from AclAuditLogEntity").executeUpdate();
        entityManager.createQuery("delete from AclEntryEntity").executeUpdate();
        entityManager.createQuery("delete from ResourceParentEntity").executeUpdate();
    }
}
