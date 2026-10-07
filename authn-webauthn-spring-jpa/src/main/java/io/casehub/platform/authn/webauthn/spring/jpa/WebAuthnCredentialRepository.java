package io.casehub.platform.authn.webauthn.spring.jpa;

import io.casehub.platform.authn.webauthn.jpa.WebAuthnCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredentialEntity, String> {

    List<WebAuthnCredentialEntity> findByActorIdAndTenancyId(String actorId, String tenancyId);
}
