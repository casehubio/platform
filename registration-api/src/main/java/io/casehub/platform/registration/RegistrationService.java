package io.casehub.platform.registration;

public interface RegistrationService {

    RegistrationResult registerWithPassword(PasswordRegistrationRequest request);

    RegistrationResult verifyEmail(String token);

    RegistrationResult approveRegistration(String userId, String tenancyId);

    RegistrationResult rejectRegistration(String userId, String tenancyId, String reason);

    InvitationCreationResult createInvitation(InvitationRequest request);

    void revokeInvitation(String id);
}
