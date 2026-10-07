package io.casehub.platform.authn;

import io.casehub.platform.api.authn.ScopeRegistry;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

@DefaultBean
@ApplicationScoped
public class NoOpScopeRegistry implements ScopeRegistry {

    @Override
    public void register(String provider, Set<String> scopes, Class<?> consumer) {}

    @Override
    public Set<String> requiredScopes(String provider) {
        return Set.of();
    }

    @Override
    public Set<String> requiredScopes(String provider, Class<?> consumer) {
        return Set.of();
    }

    @Override
    public boolean satisfies(String provider, Set<String> grantedScopes) {
        return true;
    }

    @Override
    public Set<String> missingScopes(String provider, Set<String> grantedScopes) {
        return Set.of();
    }
}
