package io.casehub.platform.registration;

import java.util.List;
import java.util.Optional;

public interface InvitationStore {
    InvitationRecord store(InvitationRecord invitation);
    Optional<InvitationRecord> findByTokenHash(String tokenHash);
    Optional<InvitationRecord> findById(String id);
    Optional<InvitationRecord> findByEmail(String email, String tenancyId);
    void consume(String tokenHash);
    List<InvitationRecord> findByTenancy(String tenancyId);
    void delete(String id);
}
