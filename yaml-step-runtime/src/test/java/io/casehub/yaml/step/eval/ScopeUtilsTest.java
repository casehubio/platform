package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.resolver.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeUtilsTest {

    private final VariableResolver base = new VariableResolver(Map.of(), Set.of());

    @Test
    void capturedListItemsResolveWithTheirOriginalTypes() {
        Map<String, Object> data = Map.of("items", List.of(
                Map.of("id", "first", "location", Map.of("lat", 51.4430))));
        var scoped = ScopeUtils.pushScope(base, "restaurants", data);
        assertThat(scoped.resolve("${restaurants.items[0].id}")).isEqualTo("first");
        assertThat(scoped.resolve("${restaurants.items[0].location.lat}")).isEqualTo(51.4430);
        assertThat(scoped.resolveString("Place ${restaurants.items[0].id}", "test"))
                .isEqualTo("Place first");
    }

    @Test
    void pushScopeWithMapRegistersExpressionContext() {
        Map<String, Object> data = Map.of("name", "test", "value", 42);
        VariableResolver scoped = ScopeUtils.pushScope(base, "match", data);

        Map<String, Object> exprCtx = scoped.buildExpressionContext();
        assertThat(exprCtx).containsKey("match");
        assertThat(exprCtx.get("match")).isEqualTo(data);
    }

    @Test
    void pushScopeWithNonMapDoesNotRegisterExpressionContext() {
        VariableResolver scoped = ScopeUtils.pushScope(base, "match", "scalar-value");

        Map<String, Object> exprCtx = scoped.buildExpressionContext();
        assertThat(exprCtx).doesNotContainKey("match");
    }

    @Test
    void pushScopePreservesVariableResolution() {
        Map<String, Object> data = Map.of("name", "test");
        VariableResolver scoped = ScopeUtils.pushScope(base, "match", data);

        String resolved = scoped.resolveString("${match.name}", "test");
        assertThat(resolved).isEqualTo("test");
    }

    @Test
    void pushScopeWithMapPreservesNestedDrilling() {
        Map<String, Object> data = Map.of("user", Map.of("name", "Alice"));
        VariableResolver scoped = ScopeUtils.pushScope(base, "match", data);

        Object resolved = scoped.resolve("${match.user.name}");
        assertThat(resolved).isEqualTo("Alice");
    }
}
