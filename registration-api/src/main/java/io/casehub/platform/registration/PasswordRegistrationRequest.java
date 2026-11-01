package io.casehub.platform.registration;

import java.util.Objects;

public record PasswordRegistrationRequest(
    String email,
    String password,
    String displayName,
    String tenancyId,
    String invitationToken
) {
    public PasswordRegistrationRequest {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(tenancyId, "tenancyId");
    }

    @Override
    public String toString() {
        return "PasswordRegistrationRequest[email=" + email +
               ", displayName=" + displayName +
               ", tenancyId=" + tenancyId + "]";
    }
}
