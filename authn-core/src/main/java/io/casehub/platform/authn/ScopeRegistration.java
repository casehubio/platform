package io.casehub.platform.authn;

import java.util.Set;

record ScopeRegistration(Set<String> scopes, Class<?> consumer) {
    ScopeRegistration {
        scopes = Set.copyOf(scopes);
    }
}
