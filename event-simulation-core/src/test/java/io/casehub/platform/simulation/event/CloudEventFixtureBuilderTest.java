package io.casehub.platform.simulation.event;

import io.cloudevents.CloudEvent;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudEventFixtureBuilderTest {

    @Test
    void fromMapBuildsCloudEventWithRequiredFields() {
        Map<String, Object> map = Map.of(
                "type", "io.casehub.work.workitem.completed",
                "source", "/simulation/event-emitter");

        CloudEvent event = CloudEventFixtureBuilder.fromMap(map);

        assertThat(event.getType()).isEqualTo("io.casehub.work.workitem.completed");
        assertThat(event.getSource().toString()).isEqualTo("/simulation/event-emitter");
    }

    @Test
    void fromMapIncludesTenancyIdExtension() {
        Map<String, Object> map = Map.of(
                "type", "test.event",
                "source", "/test",
                "tenancyid", "tenant-1");

        CloudEvent event = CloudEventFixtureBuilder.fromMap(map);

        assertThat(event.getExtension("tenancyid")).isEqualTo("tenant-1");
    }

    @Test
    void fromMapIncludesDataPayload() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "test.event");
        map.put("source", "/test");
        map.put("datacontenttype", "application/json");
        map.put("data", Map.of("workItemId", "WI-001", "caseId", "CASE-001"));

        CloudEvent event = CloudEventFixtureBuilder.fromMap(map);

        assertThat(event.getData()).isNotNull();
        assertThat(event.getDataContentType()).isEqualTo("application/json");
        String dataStr = new String(event.getData().toBytes());
        assertThat(dataStr).contains("WI-001");
    }

    @Test
    void fromMapRejectsMissingType() {
        Map<String, Object> map = Map.of("source", "/test");

        assertThatThrownBy(() -> CloudEventFixtureBuilder.fromMap(map))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void fromMapRejectsMissingSource() {
        Map<String, Object> map = Map.of("type", "test.event");

        assertThatThrownBy(() -> CloudEventFixtureBuilder.fromMap(map))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
    }

    @Test
    void toMapRoundTrips() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("type", "test.event");
        original.put("source", "/test");
        original.put("tenancyid", "tenant-1");
        original.put("datacontenttype", "application/json");
        original.put("data", Map.of("key", "value"));

        CloudEvent event = CloudEventFixtureBuilder.fromMap(original);
        Map<String, Object> roundTripped = CloudEventFixtureBuilder.toMap(event);

        assertThat(roundTripped.get("type")).isEqualTo("test.event");
        assertThat(roundTripped.get("source")).isEqualTo("/test");
        assertThat(roundTripped.get("tenancyid")).isEqualTo("tenant-1");
    }

    @Test
    void fromMapHandlesArbitraryExtensions() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "test.event");
        map.put("source", "/test");
        map.put("tenancyid", "t1");
        map.put("customext", "custom-value");

        CloudEvent event = CloudEventFixtureBuilder.fromMap(map);

        assertThat(event.getExtension("customext")).isEqualTo("custom-value");
    }
}
