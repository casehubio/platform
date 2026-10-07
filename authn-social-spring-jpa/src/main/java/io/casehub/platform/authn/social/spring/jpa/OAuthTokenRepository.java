package io.casehub.platform.authn.social.spring.jpa;

import io.casehub.platform.authn.social.jpa.OAuthTokenEntity;
import io.casehub.platform.authn.social.jpa.OAuthTokenId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OAuthTokenRepository extends JpaRepository<OAuthTokenEntity, OAuthTokenId> {

    List<OAuthTokenEntity> findByActorIdAndTenancyId(String actorId, String tenancyId);

    void deleteByActorIdAndProviderAndTenancyId(String actorId, String provider, String tenancyId);
}
