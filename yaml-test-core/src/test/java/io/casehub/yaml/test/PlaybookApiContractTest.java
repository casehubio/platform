package io.casehub.yaml.test;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookApiContractTest {

    @Test
    void runRequestDefaults() {
        var request = new PlaybookRunRequest("test.yaml", false, Map.of(), null, null);
        assertThat(request.dryRun()).isFalse();
        assertThat(request.playbookPath()).isEqualTo("test.yaml");
        assertThat(request.vars()).isEmpty();
    }

    @Test
    void runRequestConvertToConfig() {
        var request = new PlaybookRunRequest("test.yaml", true,
                Map.of("key", "val"), "corpus.yaml", "dev");
        var config = new PlaybookRunConfig(request.dryRun(), request.vars(),
                request.corpusPath(), request.simulationProfile());
        assertThat(config.dryRun()).isTrue();
        assertThat(config.vars()).containsEntry("key", "val");
        assertThat(config.corpusPath()).isEqualTo("corpus.yaml");
        assertThat(config.simulationProfile()).isEqualTo("dev");
    }

    @Test
    void runRequestDefensiveCopyOnVars() {
        var mutable = new java.util.HashMap<String, Object>();
        mutable.put("k", "v");
        var request = new PlaybookRunRequest("t.yaml", false, mutable, null, null);
        mutable.put("extra", "no");
        assertThat(request.vars()).doesNotContainKey("extra");
    }
}
