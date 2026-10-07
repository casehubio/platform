package io.casehub.platform.authn.session.spring.jpa;

import io.casehub.platform.authn.session.jpa.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<SessionEntity, String> {

    void deleteByActorIdAndTenancyId(String actorId, String tenancyId);
}
