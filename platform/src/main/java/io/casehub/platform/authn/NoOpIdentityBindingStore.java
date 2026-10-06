package io.casehub.platform.authn;

import io.casehub.platform.api.authn.IdentityBinding;
import io.casehub.platform.api.authn.IdentityBindingStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpIdentityBindingStore implements IdentityBindingStore {

    @Override
    public void bind(IdentityBinding binding) {}

    @Override
    public Optional<IdentityBinding> findByExternalId(String provider, String externalId, String tenancyId) {
        return Optional.empty();
    }

    @Override
    public Optional<IdentityBinding> findByActorId(String actorId, String provider, String tenancyId) {
        return Optional.empty();
    }

    @Override
    public void unbind(String provider, String externalId, String tenancyId) {}
}
