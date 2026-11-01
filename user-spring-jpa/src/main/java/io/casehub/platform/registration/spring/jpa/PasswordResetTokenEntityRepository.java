package io.casehub.platform.registration.spring.jpa;

import io.casehub.platform.registration.jpa.PasswordResetTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenEntityRepository extends JpaRepository<PasswordResetTokenEntity, String> {
}
