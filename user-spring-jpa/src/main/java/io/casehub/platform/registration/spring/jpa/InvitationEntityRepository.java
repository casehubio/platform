package io.casehub.platform.registration.spring.jpa;

import io.casehub.platform.registration.jpa.InvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitationEntityRepository extends JpaRepository<InvitationEntity, String> {
    Optional<InvitationEntity> findByTokenHash(String tokenHash);
    Optional<InvitationEntity> findFirstByEmailAndTenancyIdOrderByCreatedAtDesc(String email, String tenancyId);
    List<InvitationEntity> findByTenancyIdOrderByCreatedAtDesc(String tenancyId);
}
