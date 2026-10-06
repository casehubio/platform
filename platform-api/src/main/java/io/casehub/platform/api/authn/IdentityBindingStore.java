package io.casehub.platform.api.authn;

import java.util.Optional;

public interface IdentityBindingStore {
    void bind(IdentityBinding binding);
    Optional<IdentityBinding> findByExternalId(String provider, String externalId, String tenancyId);
    Optional<IdentityBinding> findByActorId(String actorId, String provider, String tenancyId);
    void unbind(String provider, String externalId, String tenancyId);
}
