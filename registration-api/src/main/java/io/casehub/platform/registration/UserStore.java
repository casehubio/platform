package io.casehub.platform.registration;

import java.util.Optional;

public interface UserStore {
    UserRecord create(UserRecord user);
    Optional<UserRecord> findById(String id, String tenancyId);
    Optional<UserRecord> findByEmail(String email, String tenancyId);
    UserRecord updateStatus(String id, String tenancyId, UserStatus newStatus);
    void delete(String id, String tenancyId);
}
