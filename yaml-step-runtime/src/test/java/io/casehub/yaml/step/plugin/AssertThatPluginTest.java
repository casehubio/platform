package io.casehub.yaml.step.plugin;

import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssertThatPluginTest {

    private final AssertThatPlugin plugin = new AssertThatPlugin();
    private final MapServiceRegistry services = new MapServiceRegistry();

    @Test
    void containsMatcherPassesWhenSubjectContainsExpected() {
        var params = Map.<String, Object>of(
                "_subject", "Moderate risk: 60% equities",
                "contains", "equities"
        );
        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void containsMatcherFailsWithStructuredMessage() {
        var params = Map.<String, Object>of(
                "_subject", "The portfolio is balanced.",
                "contains", "equities"
        );
        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isFalse();
        String msg = ((Result.Failure) result).message();
        assertThat(msg).contains("contains");
        assertThat(msg).contains("equities");
        assertThat(msg).contains("The portfolio is balanced.");
    }

    @Test
    void equalsMatcherPasses() {
        var params = Map.<String, Object>of("_subject", "hello", "equals", "hello");
        assertThat(plugin.execute(params, services).isSuccess()).isTrue();
    }

    @Test
    void equalsMatcherFails() {
        var params = Map.<String, Object>of("_subject", "hello", "equals", "world");
        assertThat(plugin.execute(params, services).isSuccess()).isFalse();
    }

    @Test
    void startsWithMatcherPasses() {
        var params = Map.<String, Object>of("_subject", "Moderate risk", "startsWith", "Moderate");
        assertThat(plugin.execute(params, services).isSuccess()).isTrue();
    }

    @Test
    void endsWithMatcherPasses() {
        var params = Map.<String, Object>of("_subject", "Moderate risk", "endsWith", "risk");
        assertThat(plugin.execute(params, services).isSuccess()).isTrue();
    }

    @Test
    void matchesRegexPasses() {
        var params = Map.<String, Object>of("_subject", "value123", "matches", "value\\d+");
        assertThat(plugin.execute(params, services).isSuccess()).isTrue();
    }

    @Test
    void matchesRegexFails() {
        var params = Map.<String, Object>of("_subject", "valueABC", "matches", "value\\d+");
        assertThat(plugin.execute(params, services).isSuccess()).isFalse();
    }

    @Test
    void hasSizeMatcherOnCollection() {
        var params = Map.<String, Object>of("_subject", List.of("a", "b", "c"), "hasSize", 3);
        assertThat(plugin.execute(params, services).isSuccess()).isTrue();
    }

    @Test
    void hasSizeMatcherFails() {
        var params = Map.<String, Object>of("_subject", List.of("a", "b"), "hasSize", 3);
        assertThat(plugin.execute(params, services).isSuccess()).isFalse();
    }

    @Test
    void greaterThanPasses() {
        var params = Map.<String, Object>of("_subject", 10, "greaterThan", 5);
        assertThat(plugin.execute(params, services).isSuccess()).isTrue();
    }

    @Test
    void greaterThanOrEqualPasses() {
        var params = Map.<String, Object>of("_subject", 5, "greaterThanOrEqual", 5);
        assertThat(plugin.execute(params, services).isSuccess()).isTrue();
    }

    @Test
    void lessThanPasses() {
        var params = Map.<String, Object>of("_subject", 3, "lessThan", 10);
        assertThat(plugin.execute(params, services).isSuccess()).isTrue();
    }

    @Test
    void notNullPasses() {
        var params = Map.<String, Object>of("_subject", "something", "not-null", true);
        assertThat(plugin.execute(params, services).isSuccess()).isTrue();
    }

    @Test
    void notNullFailsOnNull() {
        var params = new LinkedHashMap<String, Object>();
        params.put("_subject", null);
        params.put("not-null", true);
        assertThat(plugin.execute(params, services).isSuccess()).isFalse();
    }

    @Test
    void notEmptyPassesOnNonEmptyString() {
        var params = Map.<String, Object>of("_subject", "hello", "not-empty", true);
        assertThat(plugin.execute(params, services).isSuccess()).isTrue();
    }

    @Test
    void emptyPassesOnEmptyString() {
        var params = Map.<String, Object>of("_subject", "", "empty", true);
        assertThat(plugin.execute(params, services).isSuccess()).isTrue();
    }

    @Test
    void multipleMatchersAllPass() {
        var params = new LinkedHashMap<String, Object>();
        params.put("_subject", "Moderate risk: 60% equities");
        params.put("contains", "equities");
        params.put("startsWith", "Moderate");
        params.put("not-empty", true);

        assertThat(plugin.execute(params, services).isSuccess()).isTrue();
    }

    @Test
    void multipleMatchersReportsAllFailures() {
        var params = new LinkedHashMap<String, Object>();
        params.put("_subject", "The portfolio is balanced.");
        params.put("contains", "equities");
        params.put("startsWith", "Moderate");

        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isFalse();
        String msg = ((Result.Failure) result).message();
        assertThat(msg).contains("contains:");
        assertThat(msg).contains("startsWith:");
    }

    @Test
    void customMessageAppended() {
        var params = new LinkedHashMap<String, Object>();
        params.put("_subject", "wrong");
        params.put("equals", "right");
        params.put("message", "Custom failure message");

        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isFalse();
        assertThat(((Result.Failure) result).message()).contains("Custom failure message");
    }

    @Test
    void resolvedSubjectUsedForMatching() {
        var params = Map.<String, Object>of("_subject", "already resolved", "contains", "resolved");
        assertThat(plugin.execute(params, services).isSuccess()).isTrue();
    }

    @Test
    void containsOnCollectionPasses() {
        var params = Map.<String, Object>of("_subject", List.of("a", "b", "c"), "contains", "b");
        assertThat(plugin.execute(params, services).isSuccess()).isTrue();
    }

    @Test
    void stringEqualsWithNumberCoercion() {
        var params = Map.<String, Object>of("_subject", "42", "equals", 42);
        assertThat(plugin.execute(params, services).isSuccess()).isTrue();
    }
}
