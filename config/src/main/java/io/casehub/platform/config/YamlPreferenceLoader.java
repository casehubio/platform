package io.casehub.platform.config;

import io.casehub.platform.api.path.Path;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a casehub preferences YAML file into a scope-keyed map.
 *
 * <p>YAML format:
 * <pre>
 * entries:
 *   - devtown.globalDefault: "true"          # no scope key = unscoped (null map key)
 *   - scope: casehubio/devtown
 *     devtown.humanApprovalThreshold: "500"
 *   - scope: casehubio/devtown/pr-review
 *     devtown.humanApprovalThreshold: "100"
 * </pre>
 *
 * <p>Returns {@code Map<Path, Map<String, String>>} where {@code null} key = unscoped.
 * Values are raw strings — callers handle {@code ${VAR}} interpolation and type conversion.
 */
public final class YamlPreferenceLoader {

    private YamlPreferenceLoader() {}

    @SuppressWarnings("unchecked")
    public static Map<Path, Map<String, String>> load(InputStream is) {
        Map<Path, Map<String, String>> result = new HashMap<>();
        if (is == null) return result;

        Yaml yaml = new Yaml();
        Map<String, Object> doc = yaml.load(is);
        if (doc == null) return result;

        List<Map<String, Object>> entries = (List<Map<String, Object>>) doc.get("entries");
        if (entries == null) return result;

        for (Map<String, Object> entry : entries) {
            String scopeStr = (String) entry.get("scope");
            Path scopeKey = scopeStr != null ? Path.parse(scopeStr) : null;

            Map<String, String> prefs = result.computeIfAbsent(scopeKey, k -> new HashMap<>());
            entry.forEach((k, v) -> {
                if (!"scope".equals(k)) prefs.put(k, String.valueOf(v));
            });
        }
        return result;
    }
}
