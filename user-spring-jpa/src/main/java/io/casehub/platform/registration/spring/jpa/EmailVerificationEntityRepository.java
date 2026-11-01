package io.casehub.platform.registration.spring.jpa;

import io.casehub.platform.registration.jpa.EmailVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationEntityRepository extends JpaRepository<EmailVerificationEntity, String> {
}
