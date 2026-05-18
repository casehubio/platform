package io.casehub.platform.mock;

import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @DefaultBean mock for dev/test.
 *
 * <p>Typed {@code get()} always returns {@code null} — callers must fall back to the
 * Preference record's {@code DEFAULT} constant. This is by design: typed Preference
 * instances cannot be injected via SmallRye config.
 *
 * <p>{@code asMap()} returns {@code String} values suitable for CaseContext/JQ injection.
 * Config keys match {@code PreferenceKey.qualifiedName()} (namespace.name), e.g.:
 * <pre>
 *   casehub.platform.preferences.defaults.devtown.humanApprovalThreshold=500
 * </pre>
 * When no properties are configured, the map is empty.
 *
 * <p>Ignores scope hierarchy — returns the same flat map for every {@code SettingsScope}.
 * Real implementations walk {@code scope.scope().segments()} applying inheritance per level.
 */
@ApplicationScoped
@DefaultBean
public class MockPreferenceProvider implements PreferenceProvider {

    @ConfigProperty(name = "casehub.platform.preferences.defaults")
    Optional<Map<String, String>> defaults;

    @Override
    public Preferences resolve(SettingsScope scope) {
        Map<String, Object> objectMap = new HashMap<>();
        defaults.ifPresent(objectMap::putAll);
        return new MapPreferences(objectMap);
    }
}
