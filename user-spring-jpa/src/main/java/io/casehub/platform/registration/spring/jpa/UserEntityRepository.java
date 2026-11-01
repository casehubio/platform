package io.casehub.platform.registration.spring.jpa;

import io.casehub.platform.registration.jpa.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserEntityRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByEmailAndTenancyId(String email, String tenancyId);
    void deleteByIdAndTenancyId(String id, String tenancyId);
}
