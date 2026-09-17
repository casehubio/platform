package io.casehub.platform.persistence.spring.jpa;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import io.casehub.platform.persistence.jpa.PreferenceEntry;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpringPreferenceProvider implements PreferenceProvider {

    private final PreferenceEntryRepository repo;

    public SpringPreferenceProvider(PreferenceEntryRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(readOnly = true)
    public Preferences resolve(SettingsScope scope) {
        List<String> ancestors = ancestors(scope.scope());
        List<PreferenceEntry> rows = repo.findByTenancyIdAndScopeIn(scope.tenancyId(), ancestors);

        Map<String, Integer> scopeOrder = new HashMap<>();
        for (int i = 0; i < ancestors.size(); i++) {
            scopeOrder.put(ancestors.get(i), i);
        }
        rows.sort((a, b) -> Integer.compare(
                scopeOrder.getOrDefault(a.scope, -1),
                scopeOrder.getOrDefault(b.scope, -1)));

        Map<String, Object> merged = new HashMap<>();
        for (PreferenceEntry row : rows) {
            String mapKey = row.subKey.isEmpty()
                    ? row.namespace + "." + row.name
                    : row.namespace + "." + row.name + "." + row.subKey;
            merged.put(mapKey, row.value);
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
