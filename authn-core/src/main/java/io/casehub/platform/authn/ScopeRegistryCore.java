package io.casehub.platform.authn;

import io.casehub.platform.api.authn.ScopeRegistry;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ScopeRegistryCore implements ScopeRegistry {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ScopeRegistration>> registrations =
            new ConcurrentHashMap<>();

    @Override
    public void register(String provider, Set<String> scopes, Class<?> consumer) {
        java.util.Objects.requireNonNull(provider, "provider");
        java.util.Objects.requireNonNull(scopes, "scopes");
        java.util.Objects.requireNonNull(consumer, "consumer");
        registrations.computeIfAbsent(provider, k -> new CopyOnWriteArrayList<>())
                     .add(new ScopeRegistration(scopes, consumer));
    }

    @Override
    public Set<String> requiredScopes(String provider) {
        var regs = registrations.get(provider);
        if (regs == null) return Set.of();
        var all = new HashSet<String>();
        for (var reg : regs) {
            all.addAll(reg.scopes());
        }
        return Set.copyOf(all);
    }

    @Override
    public Set<String> requiredScopes(String provider, Class<?> consumer) {
        var regs = registrations.get(provider);
        if (regs == null) return Set.of();
        var all = new HashSet<String>();
        for (var reg : regs) {
            if (reg.consumer().equals(consumer)) {
                all.addAll(reg.scopes());
            }
        }
        return Set.copyOf(all);
    }

    @Override
    public boolean satisfies(String provider, Set<String> grantedScopes) {
        java.util.Objects.requireNonNull(grantedScopes, "grantedScopes");
        return grantedScopes.containsAll(requiredScopes(provider));
    }

    @Override
    public Set<String> missingScopes(String provider, Set<String> grantedScopes) {
        java.util.Objects.requireNonNull(grantedScopes, "grantedScopes");
        var required = requiredScopes(provider);
        var missing  = new HashSet<>(required);
        missing.removeAll(grantedScopes);
        return Set.copyOf(missing);
    }
}
