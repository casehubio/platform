package io.casehub.yaml.test;

import io.casehub.platform.simulation.event.CloudEventFixtureBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventCaptureTest {

    private final EventCapture capture = new EventCapture();

    @Test
    void recordsAndQueriesByType() {
        capture.addEvent(buildEvent("capacity.pressure", Map.of("p", 0.9)));
        capture.addEvent(buildEvent("capacity.pressure", Map.of("p", 0.95)));
        capture.addEvent(buildEvent("redistribution.decision", Map.of("reason", "threshold")));

        assertThat(capture.count("capacity.pressure")).isEqualTo(2);
        assertThat(capture.count("redistribution.decision")).isEqualTo(1);
        assertThat(capture.count("nonexistent")).isEqualTo(0);
    }

    @Test
    void lastReturnsLastEventOfType() {
        capture.addEvent(buildEvent("capacity.pressure", Map.of("p", 0.9)));
        capture.addEvent(buildEvent("capacity.pressure", Map.of("p", 0.95)));

        var last = capture.last("capacity.pressure");
        assertThat(last).isNotNull();
        assertThat(last.data().get("p")).isEqualTo(0.95);
    }

    @Test
    void lastReturnsNullWhenNoMatch() {
        assertThat(capture.last("nonexistent")).isNull();
    }

    @Test
    void ofTypeReturnsFilteredList() {
        capture.addEvent(buildEvent("a", Map.of()));
        capture.addEvent(buildEvent("b", Map.of()));
        capture.addEvent(buildEvent("a", Map.of()));

        assertThat(capture.ofType("a")).hasSize(2);
        assertThat(capture.ofType("b")).hasSize(1);
    }

    @Test
    void capturedEventExposesDataAsMap() {
        capture.addEvent(buildEvent("test", Map.of("reason", "threshold", "value", 42)));

        var event = capture.last("test");
        assertThat(event.data().get("reason")).isEqualTo("threshold");
        assertThat(event.data().get("value")).isEqualTo(42);
    }

    @Test
    void capturedEventExposesTypeAndSource() {
        capture.addEvent(buildEvent("test.event", Map.of()));
        var event = capture.last("test.event");
        assertThat(event.type()).isEqualTo("test.event");
        assertThat(event.source()).isNotNull();
    }

    @Test
    void clearRemovesAllEvents() {
        capture.addEvent(buildEvent("test", Map.of()));
        assertThat(capture.size()).isEqualTo(1);
        capture.clear();
        assertThat(capture.size()).isEqualTo(0);
    }

    private static io.cloudevents.CloudEvent buildEvent(String type, Map<String, Object> data) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("type", type);
        map.put("source", "/test");
        map.put("data", data);
        return CloudEventFixtureBuilder.fromMap(map);
    }
}
