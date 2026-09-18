package io.casehub.platform.simulation.testing;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import io.casehub.platform.simulation.CorpusSeed;
import io.casehub.platform.simulation.generated.PreferenceProviderQN;

import java.util.Map;

public final class PreferenceCorpus {

    private PreferenceCorpus() {}

    public static CorpusSeed<SettingsScope, Preferences> resolve(String tenancyId) {
        return new CorpusSeed<SettingsScope, Preferences>(PreferenceProviderQN.RESOLVE, tenancyId)
                .withKeyExtractor(scope -> scope.scope().value());
    }

    public static SettingsScope scope(String tenancyId, String... pathSegments) {
        return SettingsScope.of(tenancyId, Path.of(pathSegments));
    }

    public static Preferences preferences(Map<String, Object> values) {
        return new MapPreferences(values);
    }
}
