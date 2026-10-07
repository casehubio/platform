package io.casehub.platform.authn.social.spring.jpa;

import io.casehub.platform.authn.social.jpa.StaticCredentialEntity;
import io.casehub.platform.authn.social.jpa.StaticCredentialId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaticCredentialRepository extends JpaRepository<StaticCredentialEntity, StaticCredentialId> {

    List<StaticCredentialEntity> findByActorIdAndTenancyId(String actorId, String tenancyId);

    void deleteByActorIdAndProviderAndTenancyId(String actorId, String provider, String tenancyId);
}
