package io.casehub.platform.mock;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MockBeansTest {

    @Inject CurrentPrincipal principal;
    @Inject GroupMembershipProvider groupMembership;
    @Inject PreferenceProvider preferenceProvider;

    @Test
    void currentPrincipal_defaults_to_system() {
        assertEquals("system", principal.actorId());
    }

    @Test
    void currentPrincipal_isSystem_true_by_default() {
        assertTrue(principal.isSystem());
    }

    @Test
    void currentPrincipal_isAuthenticated_true_by_default() {
        assertTrue(principal.isAuthenticated());
    }

    @Test
    void currentPrincipal_groups_empty_by_default() {
        assertTrue(principal.groups().isEmpty());
    }

    @Test
    void groupMembership_always_returns_empty_set() {
        assertTrue(groupMembership.membersOf("any-group").isEmpty());
        assertTrue(groupMembership.membersOf("admin").isEmpty());
    }

    @Test
    void preferenceProvider_resolve_returns_preferences() {
        assertNotNull(preferenceProvider.resolve(SettingsScope.of("acme/backend")));
    }

    @Test
    void preferenceProvider_asMap_contains_configured_value() {
        Preferences prefs = preferenceProvider.resolve(SettingsScope.of("acme/backend"));
        assertEquals("hello", prefs.asMap().get("test.greeting"));
    }

    @Test
    void preferenceProvider_typed_get_returns_null() {
        Preferences prefs = preferenceProvider.resolve(SettingsScope.of("acme/backend"));
        record GreetingPref(String value) implements io.casehub.platform.api.preferences.SingleValuePreference {}
        PreferenceKey<GreetingPref> key = new PreferenceKey<>("test", "greeting", new GreetingPref("fallback"));
        assertNull(prefs.get(key));
        assertEquals("fallback", prefs.getOrDefault(key).value());
    }
}
