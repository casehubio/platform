package io.casehub.platform.api.authn;

import java.security.KeyPair;
import java.util.List;

public interface JwtSigningKeyResolver {
    KeyPair signingKeyPair(String tenancyId);
    String keyId(String tenancyId);
    List<PublicKeyDescriptor> publicKeys();
}
