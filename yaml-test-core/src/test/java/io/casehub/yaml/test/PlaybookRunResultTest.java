package io.casehub.yaml.test;

import io.casehub.yaml.step.runner.PlaybookRunConfig;
import io.casehub.yaml.step.runner.PlaybookRunResult;
import io.casehub.yaml.step.runner.PlaybookValidationResult;
import io.casehub.yaml.step.runner.StepOutcome;
import io.casehub.yaml.core.error.ParseError;
import io.casehub.yaml.core.error.SourceLocation;
import io.casehub.yaml.core.error.YamlError;
import io.casehub.yaml.step.runner.StepTraceNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        YamlError diag = new ParseError.UnknownStepError(null, "foo", SourceLocation.UNKNOWN, null);
        var result = new PlaybookValidationResult(false, List.of(diag), null);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("foo");
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
        mutable.put("nextCursor", null);
        var result = new PlaybookRunResult(true, "p", "p.yaml", 1,
                null, List.of(), mutable, null);
        mutable.put("extra", "no");
        assertThat(result.output()).doesNotContainKey("extra");
        assertThat(result.output()).containsEntry("nextCursor", null);
        assertThatThrownBy(() -> result.output().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validationResultDefensiveCopyOnDiagnostics() {
        var mutable = new java.util.ArrayList<YamlError>();
        mutable.add(new ParseError.StructureError(null, "err1", SourceLocation.UNKNOWN, null));
        var result = new PlaybookValidationResult(false, mutable, null);
        mutable.add(new ParseError.StructureError(null, "err2", SourceLocation.UNKNOWN, null));
        assertThat(result.diagnostics()).hasSize(1);
    }
}
