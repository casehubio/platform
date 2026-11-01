package io.casehub.platform.registration;

import java.time.Instant;
import java.util.Set;

public record InvitationRecord(
    String id,
    String tokenHash,
    String email,
    String tenancyId,
    Set<String> assignedGroups,
    Instant expiresAt,
    String createdBy,
    Instant createdAt,
    boolean consumed
) {
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
