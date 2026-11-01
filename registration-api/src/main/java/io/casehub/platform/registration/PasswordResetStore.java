package io.casehub.platform.registration;

import java.util.Optional;

public interface PasswordResetStore {
    void store(PasswordResetToken token);
    Optional<PasswordResetToken> consume(String rawToken);
}
