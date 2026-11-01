package io.casehub.platform.registration;

public sealed interface RegistrationResult {
    record Created(UserRecord user) implements RegistrationResult {}
    record PendingVerification(String userId, String email) implements RegistrationResult {}
    record PendingApproval(String userId) implements RegistrationResult {}
    record Activated(UserRecord user) implements RegistrationResult {}
    record Rejected(String userId, String reason) implements RegistrationResult {}
    record AlreadyExists(String email) implements RegistrationResult {}
    record PolicyDenied(RegistrationMode mode) implements RegistrationResult {}
}
