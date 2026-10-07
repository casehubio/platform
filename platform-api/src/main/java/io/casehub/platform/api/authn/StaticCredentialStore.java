package io.casehub.platform.api.authn;

import java.util.List;
import java.util.Optional;

public interface StaticCredentialStore {
    void store(StaticCredentialRecord record);
    Optional<StaticCredentialRecord> find(String actorId, String provider, String tenancyId);
    List<StaticCredentialRecord> findAll(String actorId, String tenancyId);
    void delete(String actorId, String provider, String tenancyId);
}
