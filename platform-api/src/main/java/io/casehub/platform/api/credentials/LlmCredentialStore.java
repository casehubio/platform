package io.casehub.platform.api.credentials;

import java.util.List;
import java.util.Map;

public interface LlmCredentialStore {
    void store(String tenancyId, String credentialRef, Map<String, String> credentials);
    Map<String, String> resolve(String tenancyId, String credentialRef);
    void delete(String tenancyId, String credentialRef);
    List<String> listRefs(String tenancyId);
}
