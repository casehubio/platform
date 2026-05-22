package io.casehub.platform.persistence.mongodb;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoDB-backed {@link PreferenceProvider}.
 *
 * <p>Resolves preferences by walking the scope hierarchy from root to most specific —
 * child scopes override parent scopes. {@code effectiveAt} from {@link SettingsScope}
 * is intentionally ignored: all documents are considered current. Time-travel support
 * requires a versioned write model (preferences-editor, issue #8).
 *
 * <p>{@code @Alternative @Priority(1)} beats the plain {@code @ApplicationScoped}
 * {@code JpaPreferenceProvider} when both modules are on the classpath, following the
 * persistence-backend CDI priority ladder (casehubio/parent#44).
 *
 * <p>Consumers add {@code casehub-platform-persistence-mongodb} as a compile-scope
 * dependency to activate — no further configuration required.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoPreferenceProvider implements PreferenceProvider {

    @Override
    public Preferences resolve(final SettingsScope scope) {
        final List<String> ancestors = ancestors(scope.scope());
        final List<MongoPreferenceDocument> docs = new ArrayList<>(MongoPreferenceDocument.findByScopes(ancestors));

        final Map<String, Integer> scopeOrder = new HashMap<>();
        for (int i = 0; i < ancestors.size(); i++) {
            scopeOrder.put(ancestors.get(i), i);
        }
        docs.sort((a, b) -> Integer.compare(
                scopeOrder.getOrDefault(a.scope, 0),
                scopeOrder.getOrDefault(b.scope, 0)));

        final Map<String, Object> merged = new HashMap<>();
        for (final MongoPreferenceDocument doc : docs) {
            final String mapKey = doc.subKey.isEmpty()
                    ? doc.namespace + "." + doc.name
                    : doc.namespace + "." + doc.name + "." + doc.subKey;
            merged.put(mapKey, doc.value);
        }

        return new MapPreferences(merged);
    }

    /** Returns ancestor scope strings shortest-first, ending with the target scope. */
    private static List<String> ancestors(final Path path) {
        final List<String> result = new ArrayList<>();
        Path current = path;
        while (current != null) {
            result.add(0, current.value());
            current = current.parent();
        }
        return result;
    }
}
