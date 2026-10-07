package io.casehub.platform.authn.inmem;

import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStaticCredentialStore implements StaticCredentialStore {

    private final ConcurrentHashMap<String, StaticCredentialRecord> store =
            new ConcurrentHashMap<>();

    @Override
    public void store(StaticCredentialRecord record) {
        store.put(key(record.actorId(), record.provider(), record.tenancyId()), record);
    }

    @Override
    public Optional<StaticCredentialRecord> find(String actorId, String provider, String tenancyId) {
        return Optional.ofNullable(store.get(key(actorId, provider, tenancyId)));
    }

    @Override
    public List<StaticCredentialRecord> findAll(String actorId, String tenancyId) {
        return store.values().stream()
                .filter(r -> r.actorId().equals(actorId) && r.tenancyId().equals(tenancyId))
                .toList();
    }

    @Override
    public void delete(String actorId, String provider, String tenancyId) {
        store.remove(key(actorId, provider, tenancyId));
    }

    private static String key(String actorId, String provider, String tenancyId) {
        return actorId + "|" + provider + "|" + tenancyId;
    }
}
