package io.casehub.platform.streams.kafka;

import io.casehub.platform.api.endpoints.EndpointCapability;
import io.casehub.platform.api.endpoints.EndpointDescriptor;
import io.casehub.platform.api.endpoints.EndpointPropertyKeys;
import io.casehub.platform.api.endpoints.EndpointProtocol;
import io.casehub.platform.api.endpoints.EndpointQuery;
import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.api.endpoints.EndpointType;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.cloudevents.CloudEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaStreamProcessorTest {

    private final List<CloudEvent> captured = new ArrayList<>();

    private EndpointDescriptor descriptor(String streamType) {
        return new EndpointDescriptor(
            Path.of("streams", "iot-events"),
            TenancyConstants.DEFAULT_TENANT_ID,
            EndpointType.SYSTEM,
            EndpointProtocol.KAFKA,
            Map.of(EndpointPropertyKeys.TOPIC, "iot-temperature",
                   EndpointPropertyKeys.STREAM_EVENT_TYPE, streamType),
            null,
            Set.of(EndpointCapability.RECEIVE));
    }

    private KafkaStreamProcessorCore coreWithDescriptors(List<EndpointDescriptor> descriptors, String topicConfig) {
        var core = new KafkaStreamProcessorCore(new StubRegistry(descriptors), captured::add,
            topicConfig.isBlank() ? Map.of() : Map.of("channel", topicConfig));
        core.init();
        return core;
    }

    @Test
    void processMessage_sets_type_from_descriptor() {
        var core = coreWithDescriptors(List.of(descriptor("io.casehub.iot.temperature")), "iot-temperature");
        core.processMessage("{}".getBytes(StandardCharsets.UTF_8), "iot-temperature", "tenant-a");
        assertThat(captured.get(0).getType()).isEqualTo("io.casehub.iot.temperature");
    }

    @Test
    void processMessage_sets_tenancyid_from_override() {
        var core = coreWithDescriptors(List.of(descriptor("io.casehub.iot.temperature")), "iot-temperature");
        core.processMessage("{}".getBytes(StandardCharsets.UTF_8), "iot-temperature", "header-tenant");
        assertThat(captured.get(0).getExtension("tenancyid")).isEqualTo("header-tenant");
    }

    @Test
    void processMessage_falls_back_to_descriptor_tenancyid() {
        var core = coreWithDescriptors(List.of(descriptor("io.casehub.iot.temperature")), "iot-temperature");
        core.processMessage("{}".getBytes(StandardCharsets.UTF_8), "iot-temperature", null);
        assertThat(captured.get(0).getExtension("tenancyid")).isEqualTo(TenancyConstants.DEFAULT_TENANT_ID);
    }

    @Test
    void processMessage_sets_data_to_raw_bytes() {
        var core = coreWithDescriptors(List.of(descriptor("io.casehub.iot.temperature")), "iot-temperature");
        core.processMessage("hello".getBytes(StandardCharsets.UTF_8), "iot-temperature", null);
        assertThat(new String(captured.get(0).getData().toBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void processMessage_source_contains_topic() {
        var core = coreWithDescriptors(List.of(descriptor("io.casehub.iot.temperature")), "iot-temperature");
        core.processMessage(new byte[0], "iot-temperature", null);
        assertThat(captured.get(0).getSource().toString()).contains("iot-temperature");
    }

    @Test
    void processMessage_withContentType_setsDataContentType() {
        EndpointDescriptor desc = new EndpointDescriptor(
            Path.of("streams", "iot-events"),
            TenancyConstants.DEFAULT_TENANT_ID,
            EndpointType.SYSTEM,
            EndpointProtocol.KAFKA,
            Map.of(EndpointPropertyKeys.TOPIC, "iot-temperature",
                   EndpointPropertyKeys.STREAM_EVENT_TYPE, "io.casehub.iot.temperature",
                   EndpointPropertyKeys.STREAM_DATA_CONTENT_TYPE, "application/avro"),
            null,
            Set.of(EndpointCapability.RECEIVE));
        var core = coreWithDescriptors(List.of(desc), "iot-temperature");
        core.processMessage(new byte[0], "iot-temperature", null);
        assertThat(captured.get(0).getDataContentType()).isEqualTo("application/avro");
    }

    @Test
    void processMessage_withoutContentType_omitsDataContentType() {
        var core = coreWithDescriptors(List.of(descriptor("io.casehub.iot.temperature")), "iot-temperature");
        core.processMessage(new byte[0], "iot-temperature", null);
        assertThat(captured.get(0).getDataContentType()).isNull();
    }

    @Test
    void processMessage_nullDescriptor_omitsDataContentType() {
        var core = coreWithDescriptors(List.of(), "");
        core.processMessage(new byte[0], "unknown-topic", null);
        assertThat(captured.get(0).getDataContentType()).isNull();
    }

    @Test
    void processMessage_unregistered_type_used_when_no_descriptor() {
        var core = coreWithDescriptors(List.of(), "");
        core.processMessage(new byte[0], "unknown-topic", null);
        assertThat(captured.get(0).getType()).isEqualTo("io.casehub.platform.streams.kafka.unregistered");
    }

    static class StubRegistry implements EndpointRegistry {
        private final List<EndpointDescriptor> descriptors;
        StubRegistry(List<EndpointDescriptor> descriptors) { this.descriptors = descriptors; }
        @Override public List<EndpointDescriptor> discover(EndpointQuery q) { return descriptors; }
        @Override public void register(EndpointDescriptor d) {}
        @Override public Optional<EndpointDescriptor> resolve(Path p, String t) { return Optional.empty(); }
        @Override public void deregister(Path p, String t) {}
    }
}
