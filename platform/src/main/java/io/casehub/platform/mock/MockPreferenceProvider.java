package io.casehub.platform.mock;

import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @DefaultBean mock for dev/test.
 *
 * <p>Typed {@code get()} always returns {@code null} — callers should use
 * {@code getOrDefault(key)} to apply the key's compile-time default automatically.
 *
 * <p>{@code asMap()} returns values in their natural Java types (Integer, Long, Boolean,
 * Double, List, String) suitable for injection into CaseContext. Config keys match
 * {@code PreferenceKey.qualifiedName()} (namespace.name), e.g.:
 * <pre>
 *   casehub.platform.preferences.defaults.devtown.humanApprovalThreshold=500
 * </pre>
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
        defaults.ifPresent(d -> d.forEach((k, v) -> objectMap.put(k, parseValue(v))));
        return new MapPreferences(objectMap);
    }

    private static Object parseValue(String s) {
        if ("true".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s)) return Boolean.FALSE;
        try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        if (s.contains(",")) {
            return Arrays.stream(s.split(","))
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.toList());
        }
        return s;
    }
}
