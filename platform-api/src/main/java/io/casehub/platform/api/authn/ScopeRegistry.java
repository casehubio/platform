package io.casehub.platform.api.authn;

import java.util.Set;

public interface ScopeRegistry {
    void register(String provider, Set<String> scopes, Class<?> consumer);
    Set<String> requiredScopes(String provider);
    Set<String> requiredScopes(String provider, Class<?> consumer);
    boolean satisfies(String provider, Set<String> grantedScopes);
    Set<String> missingScopes(String provider, Set<String> grantedScopes);
}
