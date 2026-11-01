package io.casehub.platform.registration;

public interface PasswordPolicy {
    PasswordPolicyResult validate(String password);
}
