package io.casehub.platform.registration;

public record InvitationCreationResult(
    String rawToken,
    InvitationRecord record
) {}
