package io.casehub.platform.api.authn;

import java.security.PublicKey;
import java.util.Objects;

public record PublicKeyDescriptor(
    String keyId,
    PublicKey publicKey,
    String algorithm,
    String tenancyId
) {
    public PublicKeyDescriptor {
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(algorithm, "algorithm");
    }
}
