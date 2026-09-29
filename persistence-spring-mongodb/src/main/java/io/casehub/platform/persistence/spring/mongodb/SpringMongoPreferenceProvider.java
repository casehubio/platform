package io.casehub.platform.persistence.spring.mongodb;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpringMongoPreferenceProvider implements PreferenceProvider {

    private final PreferenceDocumentRepository repo;

    public SpringMongoPreferenceProvider(PreferenceDocumentRepository repo) {
        this.repo = repo;
    }

    @Override
    public Preferences resolve(SettingsScope scope) {
        List<String> ancestors = ancestors(scope.scope());
        List<SpringPreferenceDocument> docs = new ArrayList<>(
                repo.findByTenancyIdAndScopeIn(scope.tenancyId(), ancestors));

        Map<String, Integer> scopeOrder = new HashMap<>();
        for (int i = 0; i < ancestors.size(); i++) {
            scopeOrder.put(ancestors.get(i), i);
        }
        docs.sort((a, b) -> Integer.compare(
                scopeOrder.getOrDefault(a.scope, -1),
                scopeOrder.getOrDefault(b.scope, -1)));

        Map<String, Object> merged = new HashMap<>();
        for (SpringPreferenceDocument doc : docs) {
            String mapKey = doc.subKey.isEmpty()
                    ? doc.namespace + "." + doc.name
                    : doc.namespace + "." + doc.name + "." + doc.subKey;
            merged.put(mapKey, doc.value);
        }
        return new MapPreferences(merged);
    }

    private static List<String> ancestors(Path path) {
        List<String> result = new ArrayList<>();
        Path current = path;
        while (current != null) {
            result.add(0, current.value());
            current = current.parent();
        }
        if (path.depth() > 0) {
            result.add(0, Path.root().value());
        }
        return result;
    }
}
