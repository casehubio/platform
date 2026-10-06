package io.casehub.platform.authn;

import io.casehub.platform.api.authn.JwtSigningKeyResolver;
import io.casehub.platform.api.authn.PublicKeyDescriptor;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.security.KeyPair;
import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpJwtSigningKeyResolver implements JwtSigningKeyResolver {

    @Override
    public KeyPair signingKeyPair(String tenancyId) {
        throw new UnsupportedOperationException("No JWT signing key configured");
    }

    @Override
    public String keyId(String tenancyId) {
        throw new UnsupportedOperationException("No JWT signing key configured");
    }

    @Override
    public List<PublicKeyDescriptor> publicKeys() {
        return List.of();
    }
}
