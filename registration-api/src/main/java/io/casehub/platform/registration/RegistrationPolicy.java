package io.casehub.platform.registration;

public interface RegistrationPolicy {
    RegistrationMode getMode(String tenancyId);
}
