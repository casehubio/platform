package io.casehub.yaml.core.resolver;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariableResolverTest {

    private static VariableSource mapSource(Map<String, String> values) {
        return values::get;
    }

    @Test
    void resolves_prefixed_variable() {
        var resolver = new VariableResolver(
                Map.of("var", mapSource(Map.of("batch", "500"))), Set.of());
        assertThat(resolver.resolveString("${var.batch}", "test"))
                .isEqualTo("500");
    }

    @Test
    void passes_plain_strings_through() {
        var resolver = new VariableResolver(Map.of(), Set.of());
        assertThat(resolver.resolve("plain-string")).isEqualTo("plain-string");
    }

    @Test
    void resolves_embedded_variable() {
        var resolver = new VariableResolver(
                Map.of("var", mapSource(Map.of("bucket", "prod"))), Set.of());
        assertThat(resolver.resolveString("s3://${var.bucket}/data", "node"))
                .isEqualTo("s3://prod/data");
    }

    @Test
    void resolves_multiple_variables() {
        var resolver = new VariableResolver(
                Map.of("var", mapSource(Map.of("proto", "s3", "bucket", "data"))), Set.of());
        assertThat(resolver.resolveString("${var.proto}://${var.bucket}/path", "node"))
                .isEqualTo("s3://data/path");
    }

    @Test
    void resolves_map_values() {
        var resolver = new VariableResolver(
                Map.of("var", mapSource(Map.of("uri", "s3://data"))), Set.of());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("destination", "${var.uri}");
        input.put("count", 42);
        var resolved = resolver.resolveMap(input, "node");
        assertThat(resolved).containsEntry("destination", "s3://data");
        assertThat(resolved).containsEntry("count", 42);
    }

    @Test
    void resolves_nested_map_values() {
        var resolver = new VariableResolver(
                Map.of("var", mapSource(Map.of("uri", "s3://data"))), Set.of());
        Map<String, Object> nested = Map.of("target", "${var.uri}");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("config", nested);
        var resolved = resolver.resolveMap(input, "node");
        @SuppressWarnings("unchecked")
        Map<String, Object> resolvedNested = (Map<String, Object>) resolved.get("config");
        assertThat(resolvedNested.get("target")).isEqualTo("s3://data");
    }

    @Test
    void resolves_list_values() {
        var resolver = new VariableResolver(
                Map.of("var", mapSource(Map.of("field", "email"))), Set.of());
        @SuppressWarnings("unchecked")
        List<Object> resolved = (List<Object>) resolver.resolveList(List.of("name", "${var.field}"), "node");
        assertThat(resolved).containsExactly("name", "email");
    }

    @Test
    void resolves_maps_in_lists() {
        var resolver = new VariableResolver(
                Map.of("var", mapSource(Map.of("x", "1"))), Set.of());
        var input = List.of(Map.of("val", "${var.x}"));
        var resolved = resolver.resolveList(input, "node");
        @SuppressWarnings("unchecked")
        Map<String, Object> resolvedMap = (Map<String, Object>) resolved.get(0);
        assertThat(resolvedMap.get("val")).isEqualTo("1");
    }

    @Test
    void resolve_object_dispatches_by_type() {
        var resolver = new VariableResolver(
                Map.of("var", mapSource(Map.of("x", "1"))), Set.of());
        assertThat(resolver.resolve("${var.x}")).isEqualTo("1");
        assertThat(resolver.resolve(42)).isEqualTo(42);
        assertThat(resolver.resolve(true)).isEqualTo(true);
    }

    @Test
    void non_string_values_pass_through() {
        var resolver = new VariableResolver(Map.of(), Set.of());
        assertThat(resolver.resolve(42)).isEqualTo(42);
        assertThat(resolver.resolve(3.14)).isEqualTo(3.14);
    }

    @Test
    void bare_name_throws_with_guidance() {
        var resolver = new VariableResolver(
                Map.of("var", mapSource(Map.of("x", "1"))), Set.of());
        assertThatThrownBy(() -> resolver.resolveString("${x}", "test"))
                .isInstanceOf(UnresolvedVariableException.class)
                .hasMessageContaining("${var.x}");
    }

    @Test
    void unknown_prefix_throws_listing_available() {
        var resolver = new VariableResolver(
                Map.of("var", mapSource(Map.of())), Set.of());
        assertThatThrownBy(() -> resolver.resolveString("${nope.x}", "test"))
                .isInstanceOf(UnresolvedVariableException.class)
                .hasMessageContaining("nope")
                .hasMessageContaining("var");
    }

    @Test
    void unresolved_variable_throws() {
        var resolver = new VariableResolver(
                Map.of("var", mapSource(Map.of("batch_size", "100"))), Set.of());
        assertThatThrownBy(() -> resolver.resolveString("${var.bacth_size}", "node"))
                .isInstanceOf(UnresolvedVariableException.class)
                .hasMessageContaining("bacth_size");
    }

    // --- Deferred prefixes ---

    @Test
    void deferred_prefix_passes_through() {
        var resolver = new VariableResolver(
                Map.of("var", mapSource(Map.of("region", "us-east"))),
                Set.of("match", "fault"));
        String result = resolver.resolveString(
                "${match.sink.id}-${var.region}", "rule");
        assertThat(result).isEqualTo("${match.sink.id}-us-east");
    }

    @Test
    void deferred_all_refs_passes_through() {
        var resolver = new VariableResolver(Map.of(), Set.of("match"));
        assertThat(resolver.resolveString("${match.sink.id}", "rule"))
                .isEqualTo("${match.sink.id}");
    }

    @Test
    void deferred_fault_prefix() {
        var resolver = new VariableResolver(Map.of(), Set.of("fault"));
        assertThat(resolver.resolveString("${fault.nodeId}", "rule"))
                .isEqualTo("${fault.nodeId}");
    }

    // --- Each context ---

    @Test
    void each_context_resolves() {
        var resolver = new VariableResolver(
                Map.of("var", mapSource(Map.of("batch", "1000"))), Set.of());
        var eachResolver = resolver.withEachContext(Map.of("region", "us-east"));
        assertThat(eachResolver.resolveString("s3://${each.region}/${var.batch}", "node"))
                .isEqualTo("s3://us-east/1000");
    }

    @Test
    void each_unknown_variable_throws() {
        var resolver = new VariableResolver(Map.of(), Set.of());
        var eachResolver = resolver.withEachContext(Map.of("region", "us-east"));
        assertThatThrownBy(() -> eachResolver.resolveString("${each.zone}", "node"))
                .isInstanceOf(UnresolvedVariableException.class)
                .hasMessageContaining("zone");
    }

    @Test
    void each_without_context_throws() {
        var resolver = new VariableResolver(Map.of(), Set.of());
        assertThatThrownBy(() -> resolver.resolveString("${each.region}", "node"))
                .isInstanceOf(UnresolvedVariableException.class)
                .hasMessageContaining("forEach");
    }

    // --- Each row context (CSV) ---

    @Test
    void each_row_context_drills_into_field() {
        var resolver = new VariableResolver(Map.of(), Set.of());
        var rowResolver = resolver.withEachRowContext(
                Map.of("env", Map.of("name", "staging", "region", "us-east")));
        assertThat(rowResolver.resolveString("${each.env.name}", "node"))
                .isEqualTo("staging");
        assertThat(rowResolver.resolveString("${each.env.region}", "node"))
                .isEqualTo("us-east");
    }

    @Test
    void each_row_missing_field_throws() {
        var resolver = new VariableResolver(Map.of(), Set.of());
        var rowResolver = resolver.withEachRowContext(
                Map.of("env", Map.of("name", "staging")));
        assertThatThrownBy(() -> rowResolver.resolveString("${each.env.missing}", "node"))
                .isInstanceOf(UnresolvedVariableException.class)
                .hasMessageContaining("missing")
                .hasMessageContaining("name");
    }

    @Test
    void each_row_without_field_throws_with_guidance() {
        var resolver = new VariableResolver(Map.of(), Set.of());
        var rowResolver = resolver.withEachRowContext(
                Map.of("env", Map.of("name", "staging")));
        assertThatThrownBy(() -> rowResolver.resolveString("${each.env}", "node"))
                .isInstanceOf(UnresolvedVariableException.class)
                .hasMessageContaining("field access");
    }

    // --- Chain ---

    @Test
    void chain_tries_sources_in_order() {
        var primary = mapSource(Map.of("email", "module@test.com"));
        var fallback = mapSource(Map.of("email", "global@test.com", "batch", "1000"));
        var resolver = new VariableResolver(
                Map.of("var", VariableSource.chain(primary, fallback)), Set.of());
        assertThat(resolver.resolveString("${var.email}", "test"))
                .isEqualTo("module@test.com");
        assertThat(resolver.resolveString("${var.batch}", "test"))
                .isEqualTo("1000");
    }

    // --- withScope ---

    @Test
    void withScope_adds_prefix_source() {
        var resolver = new VariableResolver(
                Map.of("var", mapSource(Map.of("x", "1"))), Set.of());
        var scoped = resolver.withScope("step", mapSource(Map.of("result", "ok")));
        assertThat(scoped.resolveString("${step.result}", "test")).isEqualTo("ok");
        assertThat(scoped.resolveString("${var.x}", "test")).isEqualTo("1");
    }

    // --- Default values ---

    @Test
    void default_value_used_when_variable_missing() {
        var resolver = new VariableResolver(
                Map.of("env", mapSource(Map.of())), Set.of());
        assertThat(resolver.resolveString("${env.MISSING:-fallback}", "test"))
                .isEqualTo("fallback");
    }

    @Test
    void default_value_ignored_when_variable_present() {
        var resolver = new VariableResolver(
                Map.of("env", mapSource(Map.of("HOST", "prod.example.com"))), Set.of());
        assertThat(resolver.resolveString("${env.HOST:-localhost}", "test"))
                .isEqualTo("prod.example.com");
    }

    @Test
    void empty_default_value() {
        var resolver = new VariableResolver(
                Map.of("env", mapSource(Map.of())), Set.of());
        assertThat(resolver.resolveString("${env.MISSING:-}", "test"))
                .isEqualTo("");
    }

    @Test
    void default_value_with_embedded_text() {
        var resolver = new VariableResolver(
                Map.of("env", mapSource(Map.of())), Set.of());
        assertThat(resolver.resolveString("host=${env.DB_HOST:-localhost}:5432", "test"))
                .isEqualTo("host=localhost:5432");
    }

    @Test
    void no_default_still_throws() {
        var resolver = new VariableResolver(
                Map.of("env", mapSource(Map.of())), Set.of());
        assertThatThrownBy(() -> resolver.resolveString("${env.MISSING}", "test"))
                .isInstanceOf(UnresolvedVariableException.class)
                .hasMessageContaining("MISSING");
    }

    // --- Built-in sources ---

    @Test
    void env_source_resolves_real_env_var() {
        var resolver = new VariableResolver(
                Map.of("env", VariableSource.env()), Set.of());
        String path = resolver.resolveString("${env.PATH}", "test");
        assertThat(path).isNotEmpty();
    }

    @Test
    void systemProperty_source_resolves() {
        var resolver = new VariableResolver(
                Map.of("sys", VariableSource.systemProperty()), Set.of());
        assertThat(resolver.resolveString("${sys.java.version}", "test"))
                .isNotEmpty();
    }

// --- DeferredPrefixHandler ---

    @Test
    void deferred_prefix_handler_invoked_on_hit() {
        var captured = new java.util.concurrent.atomic.AtomicReference<String>();
        var resolver = new VariableResolver(Map.of(), Set.of("match"))
                               .withDeferredPrefixHandler((prefix, key, ctx) ->
                                                                  captured.set(prefix + ":" + key));
        resolver.resolveString("${match.sink.id}", "rule");
        assertThat(captured.get()).isEqualTo("match:match.sink.id");
    }

    @Test
    void deferred_prefix_handler_can_throw() {
        var resolver = new VariableResolver(Map.of(), Set.of("match"))
                               .withDeferredPrefixHandler((prefix, key, ctx) -> {
                                   throw new UnresolvedVariableException(key, ctx,
                                                                         prefix + " refs resolved at runtime");
                               });
        assertThatThrownBy(() -> resolver.resolveString("${match.sink.id}", "node"))
                .isInstanceOf(UnresolvedVariableException.class)
                .hasMessageContaining("runtime");
    }

    @Test
    void no_handler_deferred_passes_through_silently() {
        var resolver = new VariableResolver(Map.of(), Set.of("match"));
        assertThat(resolver.resolveString("${match.sink.id}", "rule"))
                .isEqualTo("${match.sink.id}");
    }

    @Test
    void handler_survives_withEachContext() {
        var captured = new java.util.concurrent.atomic.AtomicReference<String>();
        var resolver = new VariableResolver(Map.of(), Set.of("match"))
                               .withDeferredPrefixHandler((prefix, key, ctx) ->
                                                                  captured.set(prefix));
        var child = resolver.withEachContext(Map.of("region", "us-east"));
        child.resolveString("${match.x}", "test");
        assertThat(captured.get()).isEqualTo("match");
    }

    @Test
    void handler_survives_withScope() {
        var captured = new java.util.concurrent.atomic.AtomicReference<String>();
        var resolver = new VariableResolver(Map.of(), Set.of("fault"))
                               .withDeferredPrefixHandler((prefix, key, ctx) ->
                                                                  captured.set(prefix));
        var child = resolver.withScope("var", name -> "val");
        child.resolveString("${fault.nodeId}", "test");
        assertThat(captured.get()).isEqualTo("fault");
    }

    @Test
    void handler_survives_withEachRowContext() {
        var captured = new java.util.concurrent.atomic.AtomicReference<String>();
        var resolver = new VariableResolver(Map.of(), Set.of("match"))
                               .withDeferredPrefixHandler((prefix, key, ctx) ->
                                                                  captured.set(prefix));
        var child = resolver.withEachRowContext(Map.of("env", Map.of("name", "prod")));
        child.resolveString("${match.x}", "test");
        assertThat(captured.get()).isEqualTo("match");
    }
}
