package io.casehub.platform.mock;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void preferenceProvider_asMap_returns_typed_values() {
        Preferences prefs = preferenceProvider.resolve(SettingsScope.of("acme/backend"));
        assertEquals(42, prefs.asMap().get("test.count"));          // Integer
        assertEquals(Boolean.TRUE, prefs.asMap().get("test.flag")); // Boolean
        assertEquals("hello", prefs.asMap().get("test.label"));     // String
    }

    @Test
    void preferenceProvider_typed_get_returns_parsed_value() {
        Preferences prefs = preferenceProvider.resolve(SettingsScope.of("acme/backend"));
        record LabelPref(String value) implements io.casehub.platform.api.preferences.SingleValuePreference {}
        PreferenceKey<LabelPref> key = new PreferenceKey<>("test", "label",
                new LabelPref("fallback"),
                LabelPref::new);
        LabelPref result = prefs.get(key);
        assertNotNull(result);
        assertEquals("hello", result.value());
    }

    @Test
    void preferenceProvider_getOrDefault_returns_parsed_value_when_configured() {
        Preferences prefs = preferenceProvider.resolve(SettingsScope.of("acme/backend"));
        record LabelPref(String value) implements io.casehub.platform.api.preferences.SingleValuePreference {}
        PreferenceKey<LabelPref> key = new PreferenceKey<>("test", "label",
                new LabelPref("fallback"),
                LabelPref::new);
        assertEquals("hello", prefs.getOrDefault(key).value());
    }

    @Test
    void preferenceProvider_getOrDefault_returns_key_default_when_absent() {
        Preferences prefs = preferenceProvider.resolve(SettingsScope.of("acme/backend"));
        record MissingPref(String value) implements io.casehub.platform.api.preferences.SingleValuePreference {}
        PreferenceKey<MissingPref> key = new PreferenceKey<>("test", "missing",
                new MissingPref("fallback"),
                MissingPref::new);
        assertEquals("fallback", prefs.getOrDefault(key).value());
    }

    @Test
    void pathParser_default_separator_is_slash() {
        // PathParserConfigurator sets / as default at startup
        Path p = Path.parse("acme/backend/pr-review");
        assertEquals(List.of("acme", "backend", "pr-review"), p.segments());
    }
}
