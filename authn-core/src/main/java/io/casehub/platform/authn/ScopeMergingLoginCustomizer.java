package io.casehub.platform.authn;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.ScopeRegistry;

import java.util.HashMap;

public class ScopeMergingLoginCustomizer {

    private final ScopeRegistry scopeRegistry;
    private final boolean enabled;

    public ScopeMergingLoginCustomizer(ScopeRegistry scopeRegistry, boolean enabled) {
        this.scopeRegistry = scopeRegistry;
        this.enabled = enabled;
    }

    public AuthenticationContext customize(AuthenticationContext context, String provider) {
        if (!enabled) return context;
        var serviceScopes = scopeRegistry.requiredScopes(provider);
        if (serviceScopes.isEmpty()) return context;
        var hints = new HashMap<>(context.hints());
        hints.put("additionalScopes", serviceScopes);
        return new AuthenticationContext(context.method(), context.tenancyId(),
            context.origin(), context.existingPrincipal(), hints);
    }
}
