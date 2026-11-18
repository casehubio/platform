package io.casehub.yaml.step.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.yaml.core.playbook.PlaybookCapabilities;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Parameter;
import io.casehub.yaml.plugin.api.ParameterType;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StepSchemaComposerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void composesSchemaWithOneOfPerPlugin() {
        PluginRegistry registry = registryWith(
                "process", Map.of(
                        "command", new Parameter(ParameterType.STRING, true, null, null, null, null)),
                "assert", Map.of(
                        "expression", new Parameter(ParameterType.STRING, true, null, null, null, null)));

        ObjectNode schema = StepSchemaComposer.compose(registry, mapper);

        assertThat(schema.has("oneOf")).isTrue();
        assertThat(schema.get("oneOf").size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void includesDecoratorProperties() {
        PluginRegistry registry = registryWith("process", Map.of());

        ObjectNode schema = StepSchemaComposer.compose(registry, mapper);

        assertThat(schema.has("properties")).isTrue();
        var props = schema.get("properties");
        assertThat(props.has("step")).isTrue();
        assertThat(props.has("if")).isTrue();
        assertThat(props.has("timeout")).isTrue();
        assertThat(props.has("wait")).as("wait decorator").isTrue();
        assertThat(props.has("mutex")).as("mutex decorator").isTrue();
        assertThat(props.has("when")).as("when decorator").isTrue();
    }

    @Test
    void includesInvokeEscapeHatch() {
        PluginRegistry registry = registryWith("process", Map.of());

        ObjectNode schema = StepSchemaComposer.compose(registry, mapper);

        boolean hasInvoke = false;
        for (var variant : schema.get("oneOf")) {
            if (variant.has("properties") && variant.get("properties").has("invoke")) {
                hasInvoke = true;
                break;
            }
        }
        assertThat(hasInvoke).isTrue();
    }

    @Test
    void emptySchemaForEmptyRegistry() {
        PluginRegistry registry = new CompositePluginRegistry();

        ObjectNode schema = StepSchemaComposer.compose(registry, mapper);

        assertThat(schema.get("oneOf").size()).isEqualTo(8);
    }

    @Test
    void pluginSchemaIncludesRequiredFields() {
        PluginRegistry registry = registryWith(
                "process", Map.of(
                        "command", new Parameter(ParameterType.STRING, true, null, null, null, null),
                        "timeout", new Parameter(ParameterType.STRING, false, null, null, null, null)));

        ObjectNode schema = StepSchemaComposer.compose(registry, mapper);

        var oneOf = schema.get("oneOf");
        for (var variant : oneOf) {
            if (variant.has("properties") && variant.get("properties").has("process")) {
                var processSchema = variant.get("properties").get("process");
                assertThat(processSchema.get("properties").has("command")).isTrue();
                assertThat(processSchema.get("properties").has("timeout")).isTrue();
                assertThat(processSchema.get("required").toString()).contains("command");
                assertThat(processSchema.get("required").toString()).doesNotContain("timeout");
                return;
            }
        }
        org.assertj.core.api.Assertions.fail("process variant not found in oneOf");
    }


    @Test
    void schemaIncludesBlockVariant() {
        PluginRegistry registry = registryWith("process", Map.of());
        ObjectNode     schema   = StepSchemaComposer.compose(registry, mapper);
        assertThat(hasVariantWithKey(schema, "block")).isTrue();
    }

    @Test
    void schemaIncludesIfThenElseVariant() {
        PluginRegistry registry = registryWith("process", Map.of());
        ObjectNode     schema   = StepSchemaComposer.compose(registry, mapper);
        boolean     hasIfThen = false;
        for (var variant : schema.get("oneOf")) {
            if (variant.has("properties") && variant.get("properties").has("if")
                && variant.get("properties").has("then")) {
                hasIfThen = true;
                break;
            }
        }
        assertThat(hasIfThen).isTrue();
    }

    @Test
    void schemaIncludesMatchCasesVariant() {
        PluginRegistry registry = registryWith("process", Map.of());
        ObjectNode     schema   = StepSchemaComposer.compose(registry, mapper);
        boolean     hasMatch = false;
        for (var variant : schema.get("oneOf")) {
            if (variant.has("properties") && variant.get("properties").has("match")
                && variant.get("properties").has("cases")) {
                hasMatch = true;
                break;
            }
        }
        assertThat(hasMatch).isTrue();
    }

    @Test
    void schemaIncludesParallelVariant() {
        PluginRegistry registry = registryWith("process", Map.of());
        ObjectNode     schema   = StepSchemaComposer.compose(registry, mapper);
        assertThat(hasVariantWithKey(schema, "parallel")).isTrue();
    }

    @Test
    void parallelVariant_supportsPolymorphicSyntax() {
        PluginRegistry registry = registryWith("process", Map.of());
        ObjectNode     schema   = StepSchemaComposer.compose(registry, mapper);

        for (var variant : schema.get("oneOf")) {
            if (variant.has("properties") && variant.get("properties").has("parallel")) {
                var parallelSchema = variant.get("properties").get("parallel");
                assertThat(parallelSchema.has("oneOf")).isTrue();
                var parallelOneOf = parallelSchema.get("oneOf");
                assertThat(parallelOneOf.size()).isEqualTo(2);

                boolean hasArray  = false;
                boolean hasObject = false;
                for (var form : parallelOneOf) {
                    if ("array".equals(form.path("type").asText())) {hasArray = true;}
                    if ("object".equals(form.path("type").asText())) {hasObject = true;}
                }
                assertThat(hasArray).as("list form").isTrue();
                assertThat(hasObject).as("map form").isTrue();

                for (var form : parallelOneOf) {
                    if ("object".equals(form.path("type").asText())) {
                        var props = form.get("properties");
                        assertThat(props.has("steps")).isTrue();
                        assertThat(props.has("advance-after")).isTrue();
                        assertThat(props.has("cancel-remaining")).isTrue();
                        assertThat(props.has("collection-timeout")).isTrue();
                    }
                }
                return;
            }
        }
        org.assertj.core.api.Assertions.fail("parallel variant not found in oneOf");
    }

    @Nested
    class CapabilityFiltering {

        private static final Set<String> ALL_CAPABILITIES = Set.of(
                PlaybookCapabilities.CONTROL_FLOW,
                PlaybookCapabilities.CONCURRENCY,
                PlaybookCapabilities.MODULES,
                PlaybookCapabilities.ORCHESTRATION,
                PlaybookCapabilities.DECORATORS);

        @Test
        void allCapabilities_matchesTwoArgOverload() {
            PluginRegistry registry = registryWith("process", Map.of());
            ObjectNode     full     = StepSchemaComposer.compose(registry, mapper);
            ObjectNode     filtered = StepSchemaComposer.compose(registry, mapper, ALL_CAPABILITIES);
            assertThat(filtered).isEqualTo(full);
        }

        @Test
        void emptyCapabilities_includesBlockAndPlugins() {
            PluginRegistry registry = registryWith("process", Map.of(
                    "command", new Parameter(ParameterType.STRING, true, null, null, null, null)));
            ObjectNode schema = StepSchemaComposer.compose(registry, mapper, Set.of());
            assertThat(hasVariantWithKey(schema, "block")).isTrue();
            assertThat(hasVariantWithKey(schema, "process")).isTrue();
        }

        @Test
        void emptyCapabilities_excludesControlFlowVariants() {
            PluginRegistry registry = registryWith("process", Map.of());
            ObjectNode     schema   = StepSchemaComposer.compose(registry, mapper, Set.of());
            assertThat(hasIfThenVariant(schema)).isFalse();
            assertThat(hasMatchCasesVariant(schema)).isFalse();
        }

        @Test
        void controlFlow_includesIfThenElseAndMatchCases() {
            PluginRegistry registry = registryWith("process", Map.of());
            ObjectNode schema = StepSchemaComposer.compose(registry, mapper,
                                                           Set.of(PlaybookCapabilities.CONTROL_FLOW));
            assertThat(hasIfThenVariant(schema)).isTrue();
            assertThat(hasMatchCasesVariant(schema)).isTrue();
        }

        @Test
        void emptyCapabilities_excludesConcurrencyVariants() {
            PluginRegistry registry = registryWith("process", Map.of());
            ObjectNode     schema   = StepSchemaComposer.compose(registry, mapper, Set.of());
            assertThat(hasVariantWithKey(schema, "parallel")).isFalse();
            assertThat(hasVariantWithKey(schema, "race")).isFalse();
        }

        @Test
        void concurrency_includesParallelAndRace() {
            PluginRegistry registry = registryWith("process", Map.of());
            ObjectNode schema = StepSchemaComposer.compose(registry, mapper,
                                                           Set.of(PlaybookCapabilities.CONCURRENCY));
            assertThat(hasVariantWithKey(schema, "parallel")).isTrue();
            assertThat(hasVariantWithKey(schema, "race")).isTrue();
        }

        @Test
        void emptyCapabilities_excludesInvokeVariant() {
            PluginRegistry registry = registryWith("process", Map.of());
            ObjectNode     schema   = StepSchemaComposer.compose(registry, mapper, Set.of());
            assertThat(hasVariantWithKey(schema, "invoke")).isFalse();
        }

        @Test
        void modules_includesInvokeVariant() {
            PluginRegistry registry = registryWith("process", Map.of());
            ObjectNode schema = StepSchemaComposer.compose(registry, mapper,
                                                           Set.of(PlaybookCapabilities.MODULES));
            assertThat(hasVariantWithKey(schema, "invoke")).isTrue();
        }

        @Test
        void emptyCapabilities_excludesOrchestrationVariants() {
            PluginRegistry registry = registryWith("process", Map.of());
            ObjectNode     schema   = StepSchemaComposer.compose(registry, mapper, Set.of());
            assertThat(hasVariantWithKey(schema, "pause")).isFalse();
            assertThat(hasVariantWithKey(schema, "resume")).isFalse();
        }

        @Test
        void orchestration_includesPauseAndResume() {
            PluginRegistry registry = registryWith("process", Map.of());
            ObjectNode schema = StepSchemaComposer.compose(registry, mapper,
                                                           Set.of(PlaybookCapabilities.ORCHESTRATION));
            assertThat(hasVariantWithKey(schema, "pause")).isTrue();
            assertThat(hasVariantWithKey(schema, "resume")).isTrue();
        }

        @Test
        void emptyCapabilities_excludesDecoratorProperties() {
            PluginRegistry registry = registryWith("process", Map.of());
            ObjectNode     schema   = StepSchemaComposer.compose(registry, mapper, Set.of());
            var            props    = schema.get("properties");
            assertThat(props.has("step")).as("step is always present").isTrue();
            assertThat(props.has("timeout")).as("timeout requires decorators").isFalse();
            assertThat(props.has("retry")).as("retry requires decorators").isFalse();
            assertThat(props.has("at")).as("at requires decorators").isFalse();
            assertThat(props.has("on-complete")).as("on-complete requires decorators").isFalse();
        }

        @Test
        void decorators_includesDecoratorProperties() {
            PluginRegistry registry = registryWith("process", Map.of());
            ObjectNode schema = StepSchemaComposer.compose(registry, mapper,
                                                           Set.of(PlaybookCapabilities.DECORATORS));
            var props = schema.get("properties");
            assertThat(props.has("step")).isTrue();
            assertThat(props.has("timeout")).isTrue();
            assertThat(props.has("retry")).isTrue();
            assertThat(props.has("at")).isTrue();
            assertThat(props.has("on-complete")).isTrue();
        }

        @Test
        void emptyCapabilities_onlyBlockAndPluginVariants() {
            PluginRegistry registry = registryWith("process", Map.of());
            ObjectNode     schema   = StepSchemaComposer.compose(registry, mapper, Set.of());
            assertThat(schema.get("oneOf").size()).isEqualTo(2);
        }

        private boolean hasIfThenVariant(ObjectNode schema) {
            for (var variant : schema.get("oneOf")) {
                if (variant.has("properties") && variant.get("properties").has("if")
                    && variant.get("properties").has("then")) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasMatchCasesVariant(ObjectNode schema) {
            for (var variant : schema.get("oneOf")) {
                if (variant.has("properties") && variant.get("properties").has("match")
                    && variant.get("properties").has("cases")) {
                    return true;
                }
            }
            return false;
        }
    }


    private boolean hasVariantWithKey(ObjectNode schema, String key) {
        for (var variant : schema.get("oneOf")) {
            if (variant.has("properties") && variant.get("properties").has(key)) {
                return true;
            }
        }
        return false;
    }

    private PluginRegistry registryWith(String name, Map<String, Parameter> inputs) {
        var registry = new CompositePluginRegistry();
        registry.register(new Definition(name, null, inputs, Map.of(), Portability.JAVA,
                (p, s) -> Result.of(Map.of()), null));
        return registry;
    }

    private PluginRegistry registryWith(String name1, Map<String, Parameter> inputs1,
                                         String name2, Map<String, Parameter> inputs2) {
        var registry = new CompositePluginRegistry();
        registry.register(new Definition(name1, null, inputs1, Map.of(), Portability.JAVA,
                (p, s) -> Result.of(Map.of()), null));
        registry.register(new Definition(name2, null, inputs2, Map.of(), Portability.JAVA,
                (p, s) -> Result.of(Map.of()), null));
        return registry;
    }
}
