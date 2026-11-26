package io.casehub.yaml.step.runner;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryActionPromptUserTest {

    @Test
    void promptUser_stores_fields() {
        var action = new RecoveryAction.PromptUser(
                "Enter your phone number", "phone", "sms",
                Map.of("format", "+1XXXXXXXXXX"));

        assertThat(action.promptText()).isEqualTo("Enter your phone number");
        assertThat(action.variableName()).isEqualTo("phone");
        assertThat(action.capability()).isEqualTo("sms");
        assertThat(action.metadata()).containsEntry("format", "+1XXXXXXXXXX");
    }

    @Test
    void promptUser_null_metadata_becomes_empty_map() {
        var action = new RecoveryAction.PromptUser(
                "Enter code", "code", "sms", null);

        assertThat(action.metadata()).isNotNull().isEmpty();
    }

    @Test
    void promptUser_metadata_is_defensive_copy() {
        var mutable = new HashMap<String, Object>();
        mutable.put("key", "value");
        var action = new RecoveryAction.PromptUser("prompt", "var", "cap", mutable);
        mutable.put("extra", "added");

        assertThat(action.metadata()).doesNotContainKey("extra");
    }

    @Test
    void recoveredWithOutput_carries_output_vars() {
        var output = Map.<String, Object>of("manual", Map.of("phone", "+1234567890"));
        var outcome = RecoveryOutcome.recoveredWithOutput(output);

        assertThat(outcome.recovered()).isTrue();
        assertThat(outcome.terminalFailure()).isNull();
        assertThat(outcome.subResult()).isNull();
        assertThat(outcome.outputVars()).containsKey("manual");
    }

    @Test
    void existing_recovered_factory_has_empty_outputVars() {
        var outcome = RecoveryOutcome.recovered(null);

        assertThat(outcome.outputVars()).isEmpty();
    }

    @Test
    void existing_notRecovered_factory_has_empty_outputVars() {
        var failure = new io.casehub.yaml.plugin.api.Result.Failure("msg");
        var outcome = RecoveryOutcome.notRecovered(failure);

        assertThat(outcome.outputVars()).isEmpty();
    }
}
