package io.casehub.platform.acl.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TestDataCleaner {

    @Transactional
    public void deleteAll() {
        AclAuditLogEntity.deleteAll();
        AclEntryEntity.deleteAll();
        ResourceParentEntity.deleteAll();
    }
}