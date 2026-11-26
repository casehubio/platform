package io.casehub.yaml.step.runner;

import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookManualPromptTest {

    @TempDir Path tempDir;

    @Test
    void manual_prompt_injects_variable_and_succeeds() throws IOException {
        Action requireAction = (params, services) ->
                Result.failed("sms not ready", "unmet-requirement",
                        Map.of("capability", "sms", "sub", List.of("receive"),
                                "options", List.of(Map.of(
                                        "type", "manual", "capability", "sms",
                                        "promptText", "Enter your phone number",
                                        "variableName", "phone"))));

        Action sendAction = (params, services) ->
                Result.of(Map.of("sent_to", params.get("to")));

        var registry = registryWith(
                definition("require-capability", requireAction),
                definition("send-code", sendAction));

        UserInputProvider inputProvider = (prompt, context) -> "+1234567890";

        var runner = new PlaybookRunnerCore(registry, new MapServiceRegistry(),
                tempDir, null, null,
                List.of(new UnmetRequirementHandler()), inputProvider);

        var mainPlaybook = tempDir.resolve("main.yaml");
        Files.writeString(mainPlaybook,
                "name: manual-sms\n---\n" +
                "- require-capability:\n    capability: sms\n" +
                "- send-code:\n    to: ${manual.phone}\n");

        var result = runner.run(mainPlaybook, PlaybookRunConfig.defaults());

        assertThat(result.success()).as("run should succeed: %s", result.error()).isTrue();
        assertThat(result.output()).containsEntry("sent_to", "+1234567890");
    }

    @Test
    void manual_prompt_user_cancels_returns_failure() throws IOException {
        Action requireAction = (params, services) ->
                Result.failed("sms not ready", "unmet-requirement",
                        Map.of("capability", "sms",
                                "options", List.of(Map.of(
                                        "type", "manual", "capability", "sms",
                                        "promptText", "Enter your phone number",
                                        "variableName", "phone"))));

        var registry = registryWith(definition("require-capability", requireAction));

        UserInputProvider inputProvider = (prompt, context) -> null;

        var runner = new PlaybookRunnerCore(registry, new MapServiceRegistry(),
                tempDir, null, null,
                List.of(new UnmetRequirementHandler()), inputProvider);

        var mainPlaybook = tempDir.resolve("main.yaml");
        Files.writeString(mainPlaybook,
                "name: test\n---\n- require-capability:\n    capability: sms\n");

        var result = runner.run(mainPlaybook, PlaybookRunConfig.defaults());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("cancelled");
    }

    @Test
    void no_input_provider_falls_back_to_unrecoverable() throws IOException {
        Action requireAction = (params, services) ->
                Result.failed("sms not ready", "unmet-requirement",
                        Map.of("capability", "sms",
                                "options", List.of(Map.of(
                                        "type", "manual", "capability", "sms",
                                        "promptText", "Enter your phone number",
                                        "variableName", "phone"))));

        var registry = registryWith(definition("require-capability", requireAction));

        var runner = new PlaybookRunnerCore(registry, new MapServiceRegistry(),
                tempDir, null, null,
                List.of(new UnmetRequirementHandler()));

        var mainPlaybook = tempDir.resolve("main.yaml");
        Files.writeString(mainPlaybook,
                "name: test\n---\n- require-capability:\n    capability: sms\n");

        var result = runner.run(mainPlaybook, PlaybookRunConfig.defaults());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("no input provider");
    }

    @Test
    void multiple_manual_prompts_accumulate_in_scope() throws IOException {
        var callCount = new AtomicInteger(0);
        Action requireAction = (params, services) -> {
            int call = callCount.getAndIncrement();
            if (call == 0) {
                return Result.failed("sms not ready", "unmet-requirement",
                        Map.of("capability", "sms",
                                "options", List.of(Map.of(
                                        "type", "manual", "capability", "sms",
                                        "promptText", "Enter phone", "variableName", "phone"))));
            }
            if (call == 1) {
                return Result.failed("email not ready", "unmet-requirement",
                        Map.of("capability", "email",
                                "options", List.of(Map.of(
                                        "type", "manual", "capability", "email",
                                        "promptText", "Enter email", "variableName", "email"))));
            }
            return Result.of(Map.of());
        };

        Action verifyAction = (params, services) ->
                Result.of(Map.of("phone_used", params.get("phone"),
                                 "email_used", params.get("email")));

        var registry = registryWith(
                definition("require-capability", requireAction),
                definition("verify", verifyAction));

        UserInputProvider inputProvider = (prompt, context) ->
                prompt.contains("phone") ? "+1234567890" : "user@example.com";

        var runner = new PlaybookRunnerCore(registry, new MapServiceRegistry(),
                tempDir, null, null,
                List.of(new UnmetRequirementHandler()), inputProvider);

        var mainPlaybook = tempDir.resolve("main.yaml");
        Files.writeString(mainPlaybook,
                "name: multi-manual\n---\n" +
                "- require-capability:\n    capability: sms\n" +
                "- require-capability:\n    capability: email\n" +
                "- verify:\n    phone: ${manual.phone}\n    email: ${manual.email}\n");

        var result = runner.run(mainPlaybook, PlaybookRunConfig.defaults());

        assertThat(result.success()).as("run should succeed: %s", result.error()).isTrue();
        assertThat(result.output()).containsEntry("phone_used", "+1234567890");
        assertThat(result.output()).containsEntry("email_used", "user@example.com");
    }

    private static PluginRegistry registryWith(Definition... defs) {
        var registry = new SimplePluginRegistry();
        for (var def : defs) { registry.register(def); }
        return registry;
    }

    private static Definition definition(String name, Action action) {
        return Definition.of(name).execute(action).absorbSiblings(true).build();
    }

    private static class SimplePluginRegistry implements PluginRegistry {
        private final java.util.concurrent.ConcurrentHashMap<String, Definition> defs = new java.util.concurrent.ConcurrentHashMap<>();
        @Override public void register(Definition definition) { defs.put(definition.name(), definition); }
        @Override public Optional<Definition> unregister(String actionName) { return Optional.ofNullable(defs.remove(actionName)); }
        @Override public Optional<Definition> resolve(String actionName) { return Optional.ofNullable(defs.get(actionName)); }
        @Override public Set<String> availableActions() { return defs.keySet(); }
    }
}
