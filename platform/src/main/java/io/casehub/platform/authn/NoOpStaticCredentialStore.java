package io.casehub.platform.authn;

import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpStaticCredentialStore implements StaticCredentialStore {

    @Override
    public void store(StaticCredentialRecord record) {}

    @Override
    public Optional<StaticCredentialRecord> find(String actorId, String provider, String tenancyId) {
        return Optional.empty();
    }

    @Override
    public List<StaticCredentialRecord> findAll(String actorId, String tenancyId) {
        return List.of();
    }

    @Override
    public void delete(String actorId, String provider, String tenancyId) {}
}
