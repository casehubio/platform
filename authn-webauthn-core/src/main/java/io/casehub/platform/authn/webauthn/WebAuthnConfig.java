package io.casehub.platform.authn.webauthn;

import java.util.Set;

public interface WebAuthnConfig {
    String rpId();
    String rpName();
    Set<String> allowedOrigins();

    default long challengeTimeoutSeconds() {
        return 300;
    }

    default int challengeLength() {
        return 32;
    }
}
