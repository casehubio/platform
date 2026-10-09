package io.casehub.yaml.step.expand;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OnCompleteExpanderTest {

    @Test
    void simpleOnComplete() {
        var parent = stepMap("build", "GATEWAY",
                "on-complete", List.of(stepMap("train", "STALKER")));

        var result = OnCompleteExpander.expand(List.of(parent));

        assertThat(result.steps()).hasSize(2);
        var expanded = result.steps().get(0);
        assertThat(expanded.get("name")).isNotNull();
        assertThat(expanded.get("signal")).isNotNull();
        assertThat(expanded).doesNotContainKey("on-complete");
        var cont = result.steps().get(1);
        assertThat(cont.get("wait")).isEqualTo(expanded.get("signal"));
    }

    @Test
    void multipleContinuations() {
        var parent = stepMap("build", "GATEWAY",
                "on-complete", List.of(
                        stepMap("train", "STALKER"),
                        stepMap("train", "SENTRY")));

        var result = OnCompleteExpander.expand(List.of(parent));
        assertThat(result.steps()).hasSize(3);
        String signal = (String) result.steps().get(0).get("signal");
        assertThat(result.steps().get(1).get("wait")).isEqualTo(signal);
        assertThat(result.steps().get(2).get("wait")).isEqualTo(signal);
    }

    @Test
    void nestedOnComplete() {
        var inner = stepMap("action", "chrono",
                "on-complete", List.of(stepMap("action", "boost")));
        var parent = stepMap("build", "NEXUS",
                "on-complete", List.of(inner));

        var result = OnCompleteExpander.expand(List.of(parent));
        assertThat(result.steps()).hasSize(3);
    }

    @Test
    void preservesExistingName() {
        var parent = new LinkedHashMap<String, Object>();
        parent.put("build", "GATEWAY");
        parent.put("name", "my-gateway");
        parent.put("on-complete", List.of(stepMap("train", "STALKER")));

        var result = OnCompleteExpander.expand(List.of(parent));
        assertThat(result.steps().get(0).get("name")).isEqualTo("my-gateway");
        assertThat(result.steps().get(1).get("wait")).isEqualTo("my-gateway_done");
    }

    @SuppressWarnings("unchecked")
    @Test
    void mergesExistingSignal() {
        var parent = new LinkedHashMap<String, Object>();
        parent.put("build", "GATEWAY");
        parent.put("signal", "existing-signal");
        parent.put("on-complete", List.of(stepMap("train", "STALKER")));

        var result = OnCompleteExpander.expand(List.of(parent));
        Object signal = result.steps().get(0).get("signal");
        assertThat(signal).isInstanceOf(List.class);
        var signals = (List<String>) signal;
        assertThat(signals).contains("existing-signal");
        assertThat(signals).hasSize(2);
    }

    @Test
    void stepsWithoutOnCompletePassThrough() {
        var step = stepMap("action", "test");
        var result = OnCompleteExpander.expand(List.of(step));
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0)).isSameAs(step);
    }

    @Test
    void sourceLocationMapTracksGeneratedNames() {
        var parent = stepMap("build", "GATEWAY",
                "on-complete", List.of(stepMap("train", "STALKER")));

        var result = OnCompleteExpander.expand(List.of(parent));
        String genName = (String) result.steps().get(0).get("name");
        assertThat(result.locations().isGenerated(genName)).isTrue();
    }

    @Test
    void continuationsPreserveExistingDecorators() {
        var cont = new LinkedHashMap<String, Object>();
        cont.put("train", "STALKER");
        cont.put("resource", "gateway");
        cont.put("priority", "high");
        var parent = stepMap("build", "GATEWAY", "on-complete", List.of(cont));

        var result = OnCompleteExpander.expand(List.of(parent));
        var expandedCont = result.steps().get(1);
        assertThat(expandedCont.get("resource")).isEqualTo("gateway");
        assertThat(expandedCont.get("priority")).isEqualTo("high");
        assertThat(expandedCont).containsKey("wait");
    }

    private static Map<String, Object> stepMap(String k1, Object v1) {
        var map = new LinkedHashMap<String, Object>();
        map.put(k1, v1);
        return map;
    }

    private static Map<String, Object> stepMap(String k1, Object v1, String k2, Object v2) {
        var map = new LinkedHashMap<String, Object>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }
}
