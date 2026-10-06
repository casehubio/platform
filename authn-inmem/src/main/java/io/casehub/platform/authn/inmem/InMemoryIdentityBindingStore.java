package io.casehub.platform.authn.inmem;

import io.casehub.platform.api.authn.IdentityBinding;
import io.casehub.platform.api.authn.IdentityBindingStore;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIdentityBindingStore implements IdentityBindingStore {

    private final ConcurrentHashMap<String, IdentityBinding> byExternalId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IdentityBinding> byActorId = new ConcurrentHashMap<>();

    @Override
    public void bind(IdentityBinding binding) {
        byExternalId.put(externalKey(binding.provider(), binding.externalId(), binding.tenancyId()), binding);
        byActorId.put(actorKey(binding.actorId(), binding.provider(), binding.tenancyId()), binding);
    }

    @Override
    public Optional<IdentityBinding> findByExternalId(String provider, String externalId, String tenancyId) {
        return Optional.ofNullable(byExternalId.get(externalKey(provider, externalId, tenancyId)));
    }

    @Override
    public Optional<IdentityBinding> findByActorId(String actorId, String provider, String tenancyId) {
        return Optional.ofNullable(byActorId.get(actorKey(actorId, provider, tenancyId)));
    }

    @Override
    public void unbind(String provider, String externalId, String tenancyId) {
        var binding = byExternalId.remove(externalKey(provider, externalId, tenancyId));
        if (binding != null) {
            byActorId.remove(actorKey(binding.actorId(), provider, tenancyId));
        }
    }

    private static String externalKey(String provider, String externalId, String tenancyId) {
        return provider + "|" + externalId + "|" + tenancyId;
    }

    private static String actorKey(String actorId, String provider, String tenancyId) {
        return actorId + "|" + provider + "|" + tenancyId;
    }

    public int size() {
        return byExternalId.size();
    }
}
