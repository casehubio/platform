package io.casehub.platform.credentials;

import io.casehub.platform.api.credentials.LlmCredentialStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;

@DefaultBean
@ApplicationScoped
public class NoOpLlmCredentialStore implements LlmCredentialStore {

    @Override
    public void store(String tenancyId, String credentialRef, Map<String, String> credentials) {}

    @Override
    public Map<String, String> resolve(String tenancyId, String credentialRef) {
        return Map.of();
    }

    @Override
    public void delete(String tenancyId, String credentialRef) {}

    @Override
    public List<String> listRefs(String tenancyId) {
        return List.of();
    }
}
