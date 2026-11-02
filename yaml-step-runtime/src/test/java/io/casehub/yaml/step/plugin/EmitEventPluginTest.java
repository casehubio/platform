package io.casehub.yaml.step.plugin;

import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Result;
import io.cloudevents.CloudEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmitEventPluginTest {

    private final EmitEventPlugin plugin = new EmitEventPlugin();
    private final List<CloudEvent> captured = new ArrayList<>();
    private final MapServiceRegistry services = new MapServiceRegistry()
            .register(EventSink.class, (EventSink) captured::add);

    @Test
    void emitsCloudEventFromScalarTypeAndData() {
        var params = new LinkedHashMap<String, Object>();
        params.put("_subject", "capacity.pressure");
        params.put("data", Map.of("actorId", "agent-001", "pressure", 0.92));

        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isTrue();
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getType()).isEqualTo("capacity.pressure");
    }

    @Test
    void emitsCloudEventFromMapForm() {
        var eventMap = new LinkedHashMap<String, Object>();
        eventMap.put("type", "test.event");
        eventMap.put("source", "/test/unit");
        eventMap.put("data", Map.of("key", "value"));

        var params = Map.<String, Object>of("_subject", eventMap);
        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isTrue();
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getType()).isEqualTo("test.event");
        assertThat(captured.get(0).getSource().toString()).isEqualTo("/test/unit");
    }

    @Test
    void usesDefaultSourceWhenNotSpecified() {
        var params = Map.<String, Object>of("_subject", "test.event");
        plugin.execute(params, services);
        assertThat(captured.get(0).getSource().toString()).isEqualTo("/test");
    }

    @Test
    void failsWithoutEventSink() {
        var emptyServices = new MapServiceRegistry();
        var params = Map.<String, Object>of("_subject", "test.event");
        Result result = plugin.execute(params, emptyServices);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void returnsEventTypeAndIdInOutput() {
        var params = Map.<String, Object>of("_subject", "test.event");
        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsKey("type");
        assertThat(result.output()).containsKey("id");
        assertThat(result.output().get("type")).isEqualTo("test.event");
    }
}
