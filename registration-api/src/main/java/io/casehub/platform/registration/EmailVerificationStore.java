package io.casehub.platform.registration;

import java.util.Optional;

public interface EmailVerificationStore {
    void store(EmailVerificationToken token);
    Optional<EmailVerificationToken> consume(String rawToken);
}
