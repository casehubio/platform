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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaStreamProcessorCoreTest {

    @Test
    void processes_message_for_registered_topic() {
        var descriptor = new EndpointDescriptor(Path.of("/kafka-test"),
            "tenant-1", EndpointType.SYSTEM, EndpointProtocol.KAFKA,
            Map.of(EndpointPropertyKeys.TOPIC, "orders",
                EndpointPropertyKeys.STREAM_EVENT_TYPE, "order.created"),
            null, Set.of(EndpointCapability.RECEIVE));

        List<CloudEvent> captured = new ArrayList<>();
        var registry = new StubRegistry(List.of(descriptor));
        var core = new KafkaStreamProcessorCore(registry, captured::add,
            Map.of("casehub-kafka-stream", "orders"));

        core.init();
        core.processMessage("payload".getBytes(), "orders", "override-tenant");

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getType()).isEqualTo("order.created");
        assertThat(captured.get(0).getExtension("tenancyid")).isEqualTo("override-tenant");
    }

    @Test
    void unregistered_topic_uses_fallback_type() {
        List<CloudEvent> captured = new ArrayList<>();
        var registry = new StubRegistry(List.of());
        var core = new KafkaStreamProcessorCore(registry, captured::add, Map.of());

        core.init();
        core.processMessage("data".getBytes(), "unknown-topic", null);

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getType()).contains("unregistered");
    }

    @Test
    void null_tenancy_id_falls_back_to_descriptor() {
        var descriptor = new EndpointDescriptor(Path.of("/kafka-test"),
            "desc-tenant", EndpointType.SYSTEM, EndpointProtocol.KAFKA,
            Map.of(EndpointPropertyKeys.TOPIC, "events",
                EndpointPropertyKeys.STREAM_EVENT_TYPE, "evt"),
            null, Set.of(EndpointCapability.RECEIVE));

        List<CloudEvent> captured = new ArrayList<>();
        var registry = new StubRegistry(List.of(descriptor));
        var core = new KafkaStreamProcessorCore(registry, captured::add,
            Map.of("channel", "events"));

        core.init();
        core.processMessage("data".getBytes(), "events", null);

        assertThat(captured.get(0).getExtension("tenancyid")).isEqualTo("desc-tenant");
    }

    @Test
    void source_uri_contains_topic_name() {
        var descriptor = new EndpointDescriptor(Path.of("/kafka-test"),
            TenancyConstants.DEFAULT_TENANT_ID, EndpointType.SYSTEM, EndpointProtocol.KAFKA,
            Map.of(EndpointPropertyKeys.TOPIC, "my-topic",
                EndpointPropertyKeys.STREAM_EVENT_TYPE, "evt"),
            null, Set.of(EndpointCapability.RECEIVE));

        List<CloudEvent> captured = new ArrayList<>();
        var registry = new StubRegistry(List.of(descriptor));
        var core = new KafkaStreamProcessorCore(registry, captured::add,
            Map.of("channel", "my-topic"));

        core.init();
        core.processMessage("data".getBytes(), "my-topic", null);

        assertThat(captured.get(0).getSource().toString()).contains("my-topic");
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
