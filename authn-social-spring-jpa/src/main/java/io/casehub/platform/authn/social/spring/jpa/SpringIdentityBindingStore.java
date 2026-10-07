package io.casehub.platform.authn.social.spring.jpa;

import io.casehub.platform.api.authn.IdentityBinding;
import io.casehub.platform.api.authn.IdentityBindingStore;
import io.casehub.platform.authn.social.jpa.IdentityBindingEntity;
import io.casehub.platform.authn.social.jpa.IdentityBindingId;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public class SpringIdentityBindingStore implements IdentityBindingStore {

    private final IdentityBindingRepository repo;

    public SpringIdentityBindingStore(IdentityBindingRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void bind(IdentityBinding binding) {
        repo.save(IdentityBindingEntity.fromRecord(binding));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdentityBinding> findByExternalId(String provider, String externalId, String tenancyId) {
        return repo.findById(new IdentityBindingId(provider, externalId, tenancyId))
                .map(IdentityBindingEntity::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdentityBinding> findByActorId(String actorId, String provider, String tenancyId) {
        return repo.findByActorIdAndProviderAndTenancyId(actorId, provider, tenancyId)
                .map(IdentityBindingEntity::toRecord);
    }

    @Override
    @Transactional
    public void unbind(String provider, String externalId, String tenancyId) {
        repo.deleteByProviderAndExternalIdAndTenancyId(provider, externalId, tenancyId);
    }
}
