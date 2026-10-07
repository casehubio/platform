package io.casehub.platform.authn;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.ScopeRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScopeMergingLoginCustomizerTest {

    @Test
    void mergesScopesWhenEnabled() {
        var registry = stubRegistry(Set.of("drive.readonly"));
        var customizer = new ScopeMergingLoginCustomizer(registry, true);
        var context = new AuthenticationContext("google", "tenant1", "https://app.example.com", Optional.empty(), Map.of());
        var result = customizer.customize(context, "google");
        @SuppressWarnings("unchecked")
        var scopes = (Set<String>) result.hints().get("additionalScopes");
        assertEquals(Set.of("drive.readonly"), scopes);
    }

    @Test
    void preservesExistingHints() {
        var registry = stubRegistry(Set.of("drive"));
        var customizer = new ScopeMergingLoginCustomizer(registry, true);
        var context = new AuthenticationContext("google", "tenant1", "https://app.example.com",
            Optional.empty(), Map.of("existingKey", "existingValue"));
        var result = customizer.customize(context, "google");
        assertEquals("existingValue", result.hints().get("existingKey"));
        assertNotNull(result.hints().get("additionalScopes"));
    }

    @Test
    void returnsUnmodifiedContextWhenDisabled() {
        var registry = stubRegistry(Set.of("drive"));
        var customizer = new ScopeMergingLoginCustomizer(registry, false);
        var context = new AuthenticationContext("google", "tenant1", "https://app.example.com", Optional.empty(), Map.of());
        var result = customizer.customize(context, "google");
        assertSame(context, result);
    }

    @Test
    void returnsUnmodifiedContextWhenNoScopes() {
        var registry = stubRegistry(Set.of());
        var customizer = new ScopeMergingLoginCustomizer(registry, true);
        var context = new AuthenticationContext("google", "tenant1", "https://app.example.com", Optional.empty(), Map.of());
        var result = customizer.customize(context, "google");
        assertSame(context, result);
    }

    private ScopeRegistry stubRegistry(Set<String> scopes) {
        return new ScopeRegistry() {
            @Override public void register(String p, Set<String> s, Class<?> c) {}
            @Override public Set<String> requiredScopes(String p) { return scopes; }
            @Override public Set<String> requiredScopes(String p, Class<?> c) { return scopes; }
            @Override public boolean satisfies(String p, Set<String> g) { return g.containsAll(scopes); }
            @Override public Set<String> missingScopes(String p, Set<String> g) { var m = new java.util.HashSet<>(scopes); m.removeAll(g); return Set.copyOf(m); }
            @Override public Set<String> registeredProviders() { return scopes.isEmpty() ? Set.of() : Set.of("google"); }
        };
    }
}
