package io.casehub.platform.acl.spring.jpa;

import io.casehub.platform.acl.jpa.ResourceParentEntity;
import io.casehub.platform.acl.jpa.ResourceParentKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceParentEntityRepository extends JpaRepository<ResourceParentEntity, ResourceParentKey> {
}
