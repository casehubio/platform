package io.casehub.yaml.step.handler;

import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.YamlDefinitionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AriaDefinitionSourceTest {

    private static final Set<String> EXPECTED_ACTIONS = Set.of(
            "click", "fill", "navigate", "select", "expand", "collapse",
            "assert", "wait", "show-markdown", "spotlight", "scroll-to-row",
            "editor-insert", "editor-set-content", "editor-replace",
            "editor-delete", "editor-cursor", "editor-highlight", "editor-completion");

    private CompositePluginRegistry registry;
    private ByteArrayOutputStream out;

    @BeforeEach
    void setUp() {
        registry = new CompositePluginRegistry();
        out = new ByteArrayOutputStream();

        var source = new YamlDefinitionSource(
                List.of("definitions/aria-actions.yaml"),
                List.of(new AriaInvokeHandler(out)),
                event -> {});
        source.populate(registry);
    }

    @Test
    void allActionsRegistered() {
        for (String action : EXPECTED_ACTIONS) {
            assertThat(registry.resolve(action))
                    .as("Action '%s' should be registered", action)
                    .isPresent();
        }
    }

    @Test
    void registeredActionsAreExecutable() {
        Definition click = registry.resolve("click").orElseThrow();
        Result result = click.action().execute(Map.of("role", "button", "name", "OK"), null);
        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(out.toString(StandardCharsets.UTF_8).trim()).isEqualTo("[click] button \"OK\"");
    }

    @Test
    void totalCount() {
        long count = EXPECTED_ACTIONS.stream()
                .filter(a -> registry.resolve(a).isPresent())
                .count();
        assertThat(count).isEqualTo(18);
    }
}
