package io.casehub.platform.registration.spring.jpa;

import io.casehub.platform.registration.jpa.PasswordCredentialEntity;
import io.casehub.platform.registration.jpa.PasswordCredentialEntityId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordCredentialEntityRepository extends JpaRepository<PasswordCredentialEntity, PasswordCredentialEntityId> {
    void deleteByActorIdAndTenancyId(String actorId, String tenancyId);
}
