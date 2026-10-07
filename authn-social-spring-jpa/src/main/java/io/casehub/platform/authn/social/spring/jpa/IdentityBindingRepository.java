package io.casehub.platform.authn.social.spring.jpa;

import io.casehub.platform.authn.social.jpa.IdentityBindingEntity;
import io.casehub.platform.authn.social.jpa.IdentityBindingId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdentityBindingRepository extends JpaRepository<IdentityBindingEntity, IdentityBindingId> {

    Optional<IdentityBindingEntity> findByActorIdAndProviderAndTenancyId(
            String actorId, String provider, String tenancyId);

    void deleteByProviderAndExternalIdAndTenancyId(
            String provider, String externalId, String tenancyId);
}
