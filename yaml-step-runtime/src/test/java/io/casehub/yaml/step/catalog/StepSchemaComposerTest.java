package io.casehub.yaml.step.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.core.step.StepParameter;
import io.casehub.yaml.core.step.StepParameterType;
import io.casehub.yaml.plugin.api.StepResult;
import io.casehub.yaml.step.CatalogEntry;
import io.casehub.yaml.step.StepCatalog;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StepSchemaComposerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void composesSchemaWithOneOfPerPlugin() {
        StepCatalog catalog = catalogWith(
                "process", Map.of(
                        "command", new StepParameter(StepParameterType.STRING, true, null, null, null, null)),
                "assert", Map.of(
                        "expression", new StepParameter(StepParameterType.STRING, true, null, null, null, null)));

        ObjectNode schema = StepSchemaComposer.compose(catalog, mapper);

        assertThat(schema.has("oneOf")).isTrue();
        assertThat(schema.get("oneOf").size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void includesDecoratorProperties() {
        StepCatalog catalog = catalogWith("process", Map.of());

        ObjectNode schema = StepSchemaComposer.compose(catalog, mapper);

        assertThat(schema.has("properties")).isTrue();
        var props = schema.get("properties");
        assertThat(props.has("step")).isTrue();
        assertThat(props.has("if")).isTrue();
        assertThat(props.has("timeout")).isTrue();
    }

    @Test
    void includesInvokeEscapeHatch() {
        StepCatalog catalog = catalogWith("process", Map.of());

        ObjectNode schema = StepSchemaComposer.compose(catalog, mapper);

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
    void emptySchemaForEmptyCatalog() {
        StepCatalog catalog = new StepCatalog() {
            @Override
            public Optional<CatalogEntry> resolve(String name) { return Optional.empty(); }

            @Override
            public Set<String> availableActions() { return Set.of(); }
        };

        ObjectNode schema = StepSchemaComposer.compose(catalog, mapper);

        assertThat(schema.get("oneOf").size()).isEqualTo(5);
    }

    @Test
    void pluginSchemaIncludesRequiredFields() {
        StepCatalog catalog = catalogWith(
                "process", Map.of(
                        "command", new StepParameter(StepParameterType.STRING, true, null, null, null, null),
                        "timeout", new StepParameter(StepParameterType.STRING, false, null, null, null, null)));

        ObjectNode schema = StepSchemaComposer.compose(catalog, mapper);

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
        StepCatalog catalog = catalogWith("process", Map.of());
        ObjectNode  schema  = StepSchemaComposer.compose(catalog, mapper);
        assertThat(hasVariantWithKey(schema, "block")).isTrue();
    }

    @Test
    void schemaIncludesIfThenElseVariant() {
        StepCatalog catalog   = catalogWith("process", Map.of());
        ObjectNode  schema    = StepSchemaComposer.compose(catalog, mapper);
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
        StepCatalog catalog  = catalogWith("process", Map.of());
        ObjectNode  schema   = StepSchemaComposer.compose(catalog, mapper);
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
        StepCatalog catalog = catalogWith("process", Map.of());
        ObjectNode  schema  = StepSchemaComposer.compose(catalog, mapper);
        assertThat(hasVariantWithKey(schema, "parallel")).isTrue();
    }

    private boolean hasVariantWithKey(ObjectNode schema, String key) {
        for (var variant : schema.get("oneOf")) {
            if (variant.has("properties") && variant.get("properties").has(key)) {
                return true;
            }
        }
        return false;
    }

    private StepCatalog catalogWith(String name, Map<String, StepParameter> inputs) {
        var def = new StepDefinition(name, null, inputs, Map.of(), new InvokeBinding.Mcp(name));
        var entry = new CatalogEntry(name, def, (p, s) -> StepResult.of(Map.of()));
        return new StepCatalog() {
            @Override
            public Optional<CatalogEntry> resolve(String n) {
                return name.equals(n) ? Optional.of(entry) : Optional.empty();
            }

            @Override
            public Set<String> availableActions() { return Set.of(name); }
        };
    }

    private StepCatalog catalogWith(String name1, Map<String, StepParameter> inputs1,
                                     String name2, Map<String, StepParameter> inputs2) {
        var def1 = new StepDefinition(name1, null, inputs1, Map.of(), new InvokeBinding.Mcp(name1));
        var entry1 = new CatalogEntry(name1, def1, (p, s) -> StepResult.of(Map.of()));
        var def2 = new StepDefinition(name2, null, inputs2, Map.of(), new InvokeBinding.Mcp(name2));
        var entry2 = new CatalogEntry(name2, def2, (p, s) -> StepResult.of(Map.of()));
        Map<String, CatalogEntry> entries = Map.of(name1, entry1, name2, entry2);
        return new StepCatalog() {
            @Override
            public Optional<CatalogEntry> resolve(String n) {
                return Optional.ofNullable(entries.get(n));
            }

            @Override
            public Set<String> availableActions() { return entries.keySet(); }
        };
    }
}
