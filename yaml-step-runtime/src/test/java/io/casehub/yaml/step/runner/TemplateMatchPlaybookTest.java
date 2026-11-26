package io.casehub.yaml.step.runner;

import io.casehub.template.registry.InMemoryTemplateRegistry;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.yaml.core.resolver.VariableConstraint;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateMatchPlaybookTest {

    private InMemoryTemplateRegistry templateRegistry;
    private List<String> executionLog;

    @BeforeEach
    void setUp() {
        templateRegistry = new InMemoryTemplateRegistry();
        templateRegistry.register(new TemplateNamespace("protocol:review",
            Map.of(
                "vote", TemplateDefinition.builder("vote")
                    .pattern("I ${decision} this ${subject}")
                    .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                    .variable("subject", VariableConstraint.string())
                    .build(),
                "escalate", TemplateDefinition.builder("escalate")
                    .pattern("escalate to ${target} because ${reason}")
                    .variable("target", VariableConstraint.string())
                    .variable("reason", VariableConstraint.string())
                    .build()
            )));
        executionLog = new ArrayList<>();
    }

    private PlaybookRunnerCore buildRunner() {
        Action logAction = (params, services) -> {
            String msg = String.valueOf(params.getOrDefault("message", ""));
            executionLog.add(msg);
            return Result.of(Map.of());
        };

        var registry = new SimplePluginRegistry();
        registry.register(Definition.of("log").execute(logAction).absorbSiblings(true).build());

        return new PlaybookRunnerCore(registry, new MapServiceRegistry(),
            null, null, null, List.of(), null, templateRegistry);
    }

    @Test
    void endToEnd_voteMatch_dispatchesCorrectHandler() {
        var runner = buildRunner();
        var config = new PlaybookRunConfig(false, Map.of("text", "I approve this architecture"), null, null);

        var result = runner.run("""
            name: vote-test
            params:
              text:
                type: string
            ---
            - match:
                input: ${params.text}
                namespace: protocol:review
                on:
                  vote:
                    - log:
                        message: vote-handler
                  escalate:
                    - log:
                        message: escalate-handler
                unmatched:
                  - log:
                      message: unmatched-handler
            """, config);

        assertThat(result.success()).as("Playbook should succeed: %s", result.error()).isTrue();
        assertThat(executionLog).containsExactly("vote-handler");
    }

    @Test
    void endToEnd_escalateMatch_dispatchesCorrectHandler() {
        var runner = buildRunner();
        var config = new PlaybookRunConfig(false, Map.of("text", "escalate to manager because critical"), null, null);

        var result = runner.run("""
            name: escalate-test
            params:
              text:
                type: string
            ---
            - match:
                input: ${params.text}
                namespace: protocol:review
                on:
                  vote:
                    - log:
                        message: vote-handler
                  escalate:
                    - log:
                        message: escalate-handler
            """, config);

        assertThat(result.success()).as("Playbook should succeed: %s", result.error()).isTrue();
        assertThat(executionLog).containsExactly("escalate-handler");
    }

    @Test
    void endToEnd_noMatch_executesUnmatchedHandler() {
        var runner = buildRunner();
        var config = new PlaybookRunConfig(false, Map.of("text", "hello world"), null, null);

        var result = runner.run("""
            name: unmatched-test
            params:
              text:
                type: string
            ---
            - match:
                input: ${params.text}
                namespace: protocol:review
                on:
                  vote:
                    - log:
                        message: vote-handler
                unmatched:
                  - log:
                      message: unmatched-handler
            """, config);

        assertThat(result.success()).as("Playbook should succeed: %s", result.error()).isTrue();
        assertThat(executionLog).containsExactly("unmatched-handler");
    }

    @Test
    void endToEnd_noMatch_noUnmatched_succeeds() {
        var runner = buildRunner();
        var config = new PlaybookRunConfig(false, Map.of("text", "hello world"), null, null);

        var result = runner.run("""
            name: no-handler-test
            params:
              text:
                type: string
            ---
            - match:
                input: ${params.text}
                namespace: protocol:review
                on:
                  vote:
                    - log:
                        message: vote-handler
            """, config);

        assertThat(result.success()).as("Playbook should succeed: %s", result.error()).isTrue();
        assertThat(executionLog).isEmpty();
    }

    @Test
    void endToEnd_inlineTemplates_registersAndMatches() {
        var runner = buildRunner();
        var config = new PlaybookRunConfig(false, Map.of("text", "I approve this architecture"), null, null);

        var result = runner.run("""
                                name: inline-template-test
                                params:
                                  text:
                                    type: string
                                templates:
                                  vote:
                                    pattern: "I ${decision} this ${subject}"
                                    variables:
                                      decision:
                                        type: enum
                                        values: [approve, reject]
                                      subject:
                                        type: string
                                ---
                                - match:
                                    input: ${params.text}
                                    namespace: inline-template-test
                                    on:
                                      vote:
                                        - log:
                                            message: vote-matched
                                    unmatched:
                                      - log:
                                          message: unmatched
                                """, config);

        assertThat(result.success()).as("Playbook should succeed: %s", result.error()).isTrue();
        assertThat(executionLog).containsExactly("vote-matched");
    }


    private static class SimplePluginRegistry implements PluginRegistry {
        private final java.util.concurrent.ConcurrentHashMap<String, Definition> defs = new java.util.concurrent.ConcurrentHashMap<>();
        @Override public void register(Definition definition) { defs.put(definition.name(), definition); }
        @Override public Optional<Definition> unregister(String actionName) { return Optional.ofNullable(defs.remove(actionName)); }
        @Override public Optional<Definition> resolve(String actionName) { return Optional.ofNullable(defs.get(actionName)); }
        @Override public Set<String> availableActions() { return defs.keySet(); }
    }
}
