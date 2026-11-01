package io.casehub.platform.registration;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record InvitationRequest(
    String email,
    String tenancyId,
    Set<String> assignedGroups,
    Instant expiresAt
) {
    public InvitationRequest {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(tenancyId, "tenancyId");
        assignedGroups = assignedGroups != null ? Set.copyOf(assignedGroups) : Set.of();
    }
}
