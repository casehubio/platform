package io.casehub.platform.llm.config;

import io.casehub.platform.api.credentials.LlmCredentialStore;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemoryLlmCredentialStore implements LlmCredentialStore {

    private final ConcurrentHashMap<String, Map<String, String>> store = new ConcurrentHashMap<>();

    @Override
    public void store(String tenancyId, String credentialRef, Map<String, String> credentials) {
        store.put(key(tenancyId, credentialRef), Map.copyOf(credentials));
    }

    @Override
    public Map<String, String> resolve(String tenancyId, String credentialRef) {
        return store.getOrDefault(key(tenancyId, credentialRef), Map.of());
    }

    @Override
    public void delete(String tenancyId, String credentialRef) {
        store.remove(key(tenancyId, credentialRef));
    }

    @Override
    public List<String> listRefs(String tenancyId) {
        String prefix = tenancyId + ":";
        return store.keySet().stream()
            .filter(k -> k.startsWith(prefix))
            .map(k -> k.substring(prefix.length()))
            .toList();
    }

    private static String key(String tenancyId, String credentialRef) {
        return tenancyId + ":" + credentialRef;
    }
}
