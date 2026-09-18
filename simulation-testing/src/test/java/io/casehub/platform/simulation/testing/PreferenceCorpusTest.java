package io.casehub.platform.simulation.testing;

import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PreferenceCorpusTest {

    @Test
    void resolveReturnsPreConfiguredSeed() {
        var seed = PreferenceCorpus.resolve("tenant-1");
        assertThat(seed.qualifiedName()).isEqualTo("preference-provider.resolve");
        assertThat(seed.keyExtractor()).isNotNull();
    }

    @Test
    void scopeCreatesSettingsScope() {
        SettingsScope scope = PreferenceCorpus.scope("tenant-1", "org", "team");
        assertThat(scope.tenancyId()).isEqualTo("tenant-1");
        assertThat(scope.scope().value()).isEqualTo("org/team");
    }

    @Test
    void preferencesCreatesMapPreferences() {
        Preferences prefs = PreferenceCorpus.preferences(Map.of("key", "value"));
        assertThat(prefs).isNotNull();
    }

    @Test
    void keyExtractorUsesScopePath() {
        var seed = PreferenceCorpus.resolve("tenant-1");
        SettingsScope scope = PreferenceCorpus.scope("tenant-1", "org", "team");
        seed.add(scope, PreferenceCorpus.preferences(Map.of()));
        assertThat(seed.build().get(0).key()).isEqualTo("org/team");
    }
}
