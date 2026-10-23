package io.casehub.platform.compiler;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DecoratorStackTest {

    @Test
    void extract_noDecorators_returnsEmpty() {
        var step = Map.<String, Object>of("rest-request", Map.of("method", "GET", "url", "/api"));
        DecoratorStack stack = DecoratorStack.extract(step);
        assertThat(stack.isEmpty()).isTrue();
    }

    @Test
    void extract_allDecorators_extractsCorrectly() {
        var step = new LinkedHashMap<String, Object>();
        step.put("rest-request", Map.of("method", "POST", "url", "/api"));
        step.put("when", "${order.valid}");
        step.put("timeout", "5s");
        step.put("retry", 3);
        step.put("mutex", "db-write");

        DecoratorStack stack = DecoratorStack.extract(step);
        assertThat(stack.when()).isEqualTo("${order.valid}");
        assertThat(stack.timeout()).isNotNull();
        assertThat(stack.timeout().toMillis()).isEqualTo(5000);
        assertThat(stack.retry()).isNotNull();
        assertThat(stack.retry().max()).isEqualTo(3);
        assertThat(stack.mutex()).isEqualTo("db-write");
    }

    @Test
    void extract_retryAsMap_extractsMax() {
        var step = new LinkedHashMap<String, Object>();
        step.put("log", Map.of("message", "hi"));
        step.put("retry", Map.of("max", 5));

        DecoratorStack stack = DecoratorStack.extract(step);
        assertThat(stack.retry().max()).isEqualTo(5);
    }

    @Test
    void strip_removesDecoratorKeys() {
        var step = new LinkedHashMap<String, Object>();
        step.put("rest-request", Map.of("method", "GET", "url", "/api"));
        step.put("when", "true");
        step.put("retry", 3);

        Map<String, Object> stripped = DecoratorStack.strip(step);
        assertThat(stripped).containsOnlyKeys("rest-request");
    }

    @Test
    void strip_removesMetaAndStep() {
        var step = new LinkedHashMap<String, Object>();
        step.put("log", Map.of("message", "hi"));
        step.put("step", "named-step");
        step.put("meta", Map.of("title", "test"));

        Map<String, Object> stripped = DecoratorStack.strip(step);
        assertThat(stripped).containsOnlyKeys("log");
    }

    @Test
    void merge_combinesNonNullFields() {
        var a = new DecoratorStack("guard", null, null, null, null);
        var b = new DecoratorStack(null, java.time.Duration.ofSeconds(5), null, null, "mx");
        DecoratorStack merged = a.merge(b);
        assertThat(merged.when()).isEqualTo("guard");
        assertThat(merged.timeout().toSeconds()).isEqualTo(5);
        assertThat(merged.mutex()).isEqualTo("mx");
    }

    @Test
    void merge_leftTakesPrecedence() {
        var a = new DecoratorStack("a", null, null, null, null);
        var b = new DecoratorStack("b", null, null, null, null);
        assertThat(a.merge(b).when()).isEqualTo("a");
    }
}
