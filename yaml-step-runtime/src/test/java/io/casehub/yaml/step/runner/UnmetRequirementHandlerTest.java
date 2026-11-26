package io.casehub.yaml.step.runner;

import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UnmetRequirementHandlerTest {

    private final UnmetRequirementHandler handler = new UnmetRequirementHandler();
    private final RecoveryContext ctx = new RecoveryContext(0, "step-1", 0, 3, Set.of());

    @Test
    void canRecover_true_for_unmet_requirement_category() {
        var failure = Result.failed("msg", "unmet-requirement");
        assertThat(handler.canRecover((Result.Failure) failure)).isTrue();
    }

    @Test
    void canRecover_false_for_other_categories() {
        var failure = Result.failed("msg", "timeout");
        assertThat(handler.canRecover((Result.Failure) failure)).isFalse();
    }

    @Test
    void canRecover_false_for_null_category() {
        var failure = Result.failed("msg");
        assertThat(handler.canRecover((Result.Failure) failure)).isFalse();
    }

    @Test
    void diagnose_returns_RunPlaybook_for_provider_with_setup_playbook() {
        var failure = failureWith(List.of(
                Map.of("type", "provider", "connector-id", "numbrrs",
                        "status", "UNCONFIGURED", "setup-playbook", "playbooks/numbrrs-setup.yaml")));

        var action = handler.diagnose((Result.Failure) failure, ctx);

        assertThat(action).isInstanceOf(RecoveryAction.RunPlaybook.class);
        var rp = (RecoveryAction.RunPlaybook) action;
        assertThat(rp.playbookPath()).isEqualTo("playbooks/numbrrs-setup.yaml");
        assertThat(rp.vars()).containsEntry("capability", "sms");
        assertThat(rp.vars()).containsEntry("provider", "numbrrs");
    }

    @Test
    void diagnose_picks_first_provider_with_setup_playbook() {
        var failure = failureWith(List.of(
                Map.of("type", "provider", "connector-id", "broken", "status", "UNCONFIGURED"),
                Map.of("type", "provider", "connector-id", "numbrrs",
                        "status", "UNCONFIGURED", "setup-playbook", "setup.yaml")));

        var action = handler.diagnose((Result.Failure) failure, ctx);

        assertThat(action).isInstanceOf(RecoveryAction.RunPlaybook.class);
        assertThat(((RecoveryAction.RunPlaybook) action).playbookPath()).isEqualTo("setup.yaml");
    }

    @Test
    void diagnose_returns_Unrecoverable_for_manual_only() {
        var failure = failureWith(List.of(
                Map.of("type", "manual", "capability", "sms")));

        var action = handler.diagnose((Result.Failure) failure, ctx);

        assertThat(action).isInstanceOf(RecoveryAction.Unrecoverable.class);
        var u = (RecoveryAction.Unrecoverable) action;
        assertThat(u.reason()).contains("manual resolution");
        assertThat(u.enrichedDetail()).containsKey("manualOptions");
    }

    @Test
    void diagnose_returns_Unrecoverable_for_empty_options() {
        var failure = Result.failed("msg", "unmet-requirement", Map.of("capability", "sms"));

        var action = handler.diagnose((Result.Failure) failure, ctx);

        assertThat(action).isInstanceOf(RecoveryAction.Unrecoverable.class);
    }

    @Test
    void diagnose_forwards_capability_and_sub_in_vars() {
        var failure = failureWith(List.of(
                Map.of("type", "provider", "connector-id", "numbrrs",
                        "setup-playbook", "setup.yaml")));

        var action = handler.diagnose((Result.Failure) failure, ctx);

        var rp = (RecoveryAction.RunPlaybook) action;
        assertThat(rp.vars()).containsEntry("capability", "sms");
        assertThat(rp.vars()).containsEntry("sub", List.of("receive"));
    }

    @Test
    void diagnose_returns_PromptUser_for_manual_with_promptText() {
        var failure = failureWith(List.of(
                Map.of("type", "manual", "capability", "sms",
                       "promptText", "Enter your phone number",
                       "variableName", "phone")));

        var action = handler.diagnose((Result.Failure) failure, ctx);

        assertThat(action).isInstanceOf(RecoveryAction.PromptUser.class);
        var pu = (RecoveryAction.PromptUser) action;
        assertThat(pu.promptText()).isEqualTo("Enter your phone number");
        assertThat(pu.variableName()).isEqualTo("phone");
        assertThat(pu.capability()).isEqualTo("sms");
    }

    @Test
    void diagnose_returns_PromptUser_variableName_defaults_to_capability() {
        var failure = failureWith(List.of(
                Map.of("type", "manual", "capability", "sms",
                       "promptText", "Enter your phone number")));

        var action = handler.diagnose((Result.Failure) failure, ctx);

        assertThat(action).isInstanceOf(RecoveryAction.PromptUser.class);
        assertThat(((RecoveryAction.PromptUser) action).variableName()).isEqualTo("sms");
    }

    @Test
    void diagnose_returns_Unrecoverable_for_manual_without_promptText() {
        var failure = failureWith(List.of(
                Map.of("type", "manual", "capability", "sms")));

        var action = handler.diagnose((Result.Failure) failure, ctx);

        assertThat(action).isInstanceOf(RecoveryAction.Unrecoverable.class);
    }

    @Test
    void diagnose_prefers_provider_over_manual() {
        var failure = failureWith(List.of(
                Map.of("type", "manual", "capability", "sms",
                       "promptText", "Enter phone"),
                Map.of("type", "provider", "connector-id", "numbrrs",
                       "setup-playbook", "setup.yaml")));

        var action = handler.diagnose((Result.Failure) failure, ctx);

        assertThat(action).isInstanceOf(RecoveryAction.RunPlaybook.class);
    }


    @SuppressWarnings("unchecked")
    private static Result failureWith(List<Map<String, Object>> options) {
        return Result.failed("Capability 'sms' is not satisfied", "unmet-requirement",
                Map.of("capability", "sms", "sub", List.of("receive"), "options", options));
    }
}
