package io.casehub.yaml.test;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookRunResultTest {

    @Test
    void configDefaultsAreNotDryRun() {
        var config = PlaybookRunConfig.defaults();
        assertThat(config.dryRun()).isFalse();
        assertThat(config.vars()).isEmpty();
        assertThat(config.corpusPath()).isNull();
        assertThat(config.simulationProfile()).isNull();
    }

    @Test
    void configDryRunFactorySetsDryRunTrue() {
        var config = PlaybookRunConfig.forDryRun();
        assertThat(config.dryRun()).isTrue();
    }

    @Test
    void validationResultReportsErrors() {
        var result = new PlaybookValidationResult(false,
                List.of("Unknown key 'foo'"), null);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("Unknown key 'foo'");
    }

    @Test
    void successfulRunResult() {
        var trace = StepTraceNode.leaf(0, "s", "plugin", "act",
                StepOutcome.EXECUTED, 5, Map.of());
        var result = new PlaybookRunResult(true, "test-playbook",
                "test.yaml", 10, trace, List.of(), Map.of("key", "val"), null);
        assertThat(result.success()).isTrue();
        assertThat(result.trace().stepCount()).isEqualTo(1);
        assertThat(result.output()).containsEntry("key", "val");
    }

    @Test
    void runResultDefensiveCopyOnOutput() {
        var mutable = new java.util.HashMap<String, Object>();
        mutable.put("k", "v");
        var result = new PlaybookRunResult(true, "p", "p.yaml", 1,
                null, List.of(), mutable, null);
        mutable.put("extra", "no");
        assertThat(result.output()).doesNotContainKey("extra");
    }

    @Test
    void validationResultDefensiveCopyOnErrors() {
        var mutable = new java.util.ArrayList<String>();
        mutable.add("err1");
        var result = new PlaybookValidationResult(false, mutable, null);
        mutable.add("err2");
        assertThat(result.errors()).hasSize(1);
    }
}
