package io.casehub.platform.config;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scope-aware YAML + SmallRye Config {@link PreferenceProvider}.
 *
 * <p>Reads one or more YAML files at startup (comma-separated list in
 * {@code casehub.platform.config.files}). Files processed left to right —
 * later files win per key+scope (chaining). After loading YAML, checks
 * {@code casehub.platform.preferences.defaults.*} SmallRye Config entries as
 * highest-priority overrides — same key format as {@code MockPreferenceProvider},
 * so {@code application.properties} overrides work in tests without code changes.
 *
 * <p>{@code @ApplicationScoped} (no {@code @DefaultBean}) displaces {@code MockPreferenceProvider}
 * automatically when {@code casehub-platform-config} is on the classpath.
 *
 * <p>Resolution priority (lowest to highest):
 * <ol>
 *   <li>Unscoped YAML entries (null scope in YAML)</li>
 *   <li>YAML scope hierarchy (root → app → case-type; child wins parent via {@code putAll})</li>
 *   <li>{@code casehub.platform.preferences.defaults.*} from SmallRye Config</li>
 * </ol>
 *
 * <p>Values stored as strings; {@code key.parse()} converts to typed values on access
 * via {@link MapPreferences#get(io.casehub.platform.api.preferences.PreferenceKey)}.
 */
@ApplicationScoped
public class ConfigFilePreferenceProvider implements PreferenceProvider {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    @ConfigProperty(name = "casehub.platform.config.files")
    Optional<List<String>> configFiles;

    @ConfigProperty(name = "casehub.platform.preferences.defaults")
    Optional<Map<String, String>> smDefaults;

    // null key = unscoped; Path key = scope-specific
    private final Map<Path, Map<String, String>> loaded = new HashMap<>();

    @PostConstruct
    void load() {
        configFiles.ifPresent(files ->
            files.forEach(fileSpec -> {
                try (InputStream is = openStream(fileSpec)) {
                    Map<Path, Map<String, String>> parsed = YamlPreferenceLoader.load(is);
                    // Later file wins per key+scope via putAll
                    parsed.forEach((scope, prefs) ->
                        loaded.computeIfAbsent(scope, k -> new HashMap<>()).putAll(prefs));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load preferences from: " + fileSpec, e);
                }
            })
        );
        // Apply ${VAR} interpolation to all loaded string values
        loaded.forEach((scope, prefs) -> prefs.replaceAll((k, v) -> interpolate(v)));
    }

    @Override
    public Preferences resolve(SettingsScope scope) {
        Map<String, Object> merged = new HashMap<>();

        // 1. Unscoped entries (lowest priority)
        Map<String, String> unscoped = loaded.get(null);
        if (unscoped != null) merged.putAll(unscoped);

        // 2. Scope hierarchy: walk from root to most specific (child putAll overwrites parent)
        collectScoped(merged, scope.scope());

        // 3. SmallRye Config overrides (highest priority — unscoped, applies to all resolve() calls)
        smDefaults.ifPresent(overrides -> merged.putAll(overrides));

        return new MapPreferences(merged); // MapPreferences copies internally
    }

    private void collectScoped(Map<String, Object> merged, Path path) {
        Path parent = path.parent();
        if (parent != null) collectScoped(merged, parent);
        Map<String, String> level = loaded.get(path);
        if (level != null) merged.putAll(level);
    }

    private static InputStream openStream(String fileSpec) throws Exception {
        if (fileSpec.startsWith("classpath:")) {
            String resource = fileSpec.substring("classpath:".length());
            InputStream is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(resource);
            if (is == null) {
                throw new IllegalArgumentException("Classpath resource not found: " + resource);
            }
            return is;
        }
        return new FileInputStream(fileSpec);
    }

    /** Replaces {@code ${KEY}} with system property, then env var. Leaves unresolved refs as-is. */
    static String interpolate(String value) {
        if (value == null || !value.contains("${")) return value;
        Matcher m = VAR_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String replacement = System.getProperty(key);
            if (replacement == null) replacement = System.getenv(key);
            if (replacement == null) replacement = m.group(0);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
