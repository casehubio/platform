package io.casehub.yaml.step.catalog;

import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.core.step.StepParameter;
import io.casehub.yaml.core.step.StepParameterType;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.CatalogEntry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolSourceTest {

    @Test
    void populatesEntriesFromDefinitions() {
        StepDefinition def = new StepDefinition("acl_canAccess", "Check access",
                Map.of("actorId", new StepParameter(StepParameterType.STRING, true, null, null, null, "Actor ID"),
                       "resourceId", new StepParameter(StepParameterType.STRING, true, null, null, null, "Resource ID")),
                Map.of(), new InvokeBinding.Mcp("acl_canAccess"));

        BiFunction<String, Map<String, Object>, Map<String, Object>> invoker =
                (name, params) -> Map.of("allowed", true);

        McpToolSource source = new McpToolSource(Map.of("acl_canAccess", def), invoker);

        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        source.populate(entries);

        assertThat(entries).containsKey("acl_canAccess");
        CatalogEntry entry = entries.get("acl_canAccess");
        assertThat(entry.definition().inputs()).containsKey("actorId");
        assertThat(entry.definition().inputs()).containsKey("resourceId");
    }

    @Test
    void actionPassesParamsToInvoker() {
        StepDefinition def = new StepDefinition("acl_canAccess", null,
                Map.of("actorId", new StepParameter(StepParameterType.STRING, true, null, null, null, null)),
                Map.of(), new InvokeBinding.Mcp("acl_canAccess"));

        AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
        BiFunction<String, Map<String, Object>, Map<String, Object>> invoker =
                (name, params) -> { captured.set(params); return Map.of("ok", true); };

        McpToolSource source = new McpToolSource(Map.of("acl_canAccess", def), invoker);

        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        source.populate(entries);

        Map<String, Object> params = Map.of("actorId", "user-1", "resourceId", "case:42");
        Result              result = entries.get("acl_canAccess").action().execute(params, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(captured.get()).containsEntry("actorId", "user-1");
    }

    @Test
    void actionHandlesInvokerException() {
        StepDefinition def = new StepDefinition("bad_tool", null,
                Map.of(), Map.of(), new InvokeBinding.Mcp("bad_tool"));

        McpToolSource source = new McpToolSource(Map.of("bad_tool", def),
                (name, params) -> { throw new RuntimeException("connection refused"); });

        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        source.populate(entries);

        Result result = entries.get("bad_tool").action().execute(Map.of(), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result).isInstanceOf(Result.Failure.class);
    }

    @Test
    void firstWriteWins() {
        StepDefinition def = new StepDefinition("tool", null, Map.of(), Map.of(),
                new InvokeBinding.Mcp("tool"));

        McpToolSource source = new McpToolSource(Map.of("tool", def),
                (name, params) -> Map.of());

        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        entries.put("tool", new CatalogEntry("tool",
                new StepDefinition("tool", "existing", Map.of(), Map.of(), null),
                (params, services) -> Result.of(Map.of())));

        source.populate(entries);

        assertThat(entries.get("tool").definition().description()).isEqualTo("existing");
    }

    @Test
    void priorityIs300() {
        McpToolSource source = new McpToolSource(Map.of(), (n, p) -> Map.of());
        assertThat(source.priority()).isEqualTo(300);
    }
}
