package io.casehub.platform.streams.amqp;

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

class AmqpStreamProcessorTest {

    private final List<CloudEvent> captured = new ArrayList<>();

    private EndpointDescriptor descriptor(String queue, String streamType) {
        return new EndpointDescriptor(
            Path.of("streams", "amqp-events"),
            TenancyConstants.DEFAULT_TENANT_ID,
            EndpointType.SYSTEM,
            EndpointProtocol.AMQP,
            Map.of(EndpointPropertyKeys.TOPIC, queue,
                   EndpointPropertyKeys.STREAM_EVENT_TYPE, streamType),
            null,
            Set.of(EndpointCapability.RECEIVE));
    }

    private AmqpStreamProcessorCore coreWithDescriptors(List<EndpointDescriptor> descriptors, String address) {
        var core = new AmqpStreamProcessorCore(new StubRegistry(descriptors), captured::add,
            address.isBlank() ? Map.of() : Map.of("channel", address));
        core.init();
        return core;
    }

    @Test
    void processMessage_sets_type_from_descriptor() {
        var core = coreWithDescriptors(List.of(descriptor("orders", "io.casehub.orders.placed")), "orders");
        core.processMessage(new byte[0], "orders", "tenant-a");
        assertThat(captured.get(0).getType()).isEqualTo("io.casehub.orders.placed");
    }

    @Test
    void processMessage_uses_tenancyid_override() {
        var core = coreWithDescriptors(List.of(descriptor("orders", "io.casehub.orders.placed")), "orders");
        core.processMessage(new byte[0], "orders", "hdr-tenant");
        assertThat(captured.get(0).getExtension("tenancyid")).isEqualTo("hdr-tenant");
    }

    @Test
    void processMessage_falls_back_to_descriptor_tenancyid() {
        var core = coreWithDescriptors(List.of(descriptor("orders", "io.casehub.orders.placed")), "orders");
        core.processMessage(new byte[0], "orders", null);
        assertThat(captured.get(0).getExtension("tenancyid")).isEqualTo(TenancyConstants.DEFAULT_TENANT_ID);
    }

    @Test
    void processMessage_source_is_amqp_prefixed() {
        var core = coreWithDescriptors(List.of(descriptor("orders", "io.casehub.orders.placed")), "orders");
        core.processMessage(new byte[0], "orders", null);
        assertThat(captured.get(0).getSource().toString()).startsWith("/platform/streams/amqp/");
    }

    @Test
    void processMessage_withContentType_setsDataContentType() {
        EndpointDescriptor desc = new EndpointDescriptor(
            Path.of("streams", "amqp-events"),
            TenancyConstants.DEFAULT_TENANT_ID,
            EndpointType.SYSTEM,
            EndpointProtocol.AMQP,
            Map.of(EndpointPropertyKeys.TOPIC, "orders",
                   EndpointPropertyKeys.STREAM_EVENT_TYPE, "io.casehub.orders.placed",
                   EndpointPropertyKeys.STREAM_DATA_CONTENT_TYPE, "application/json"),
            null,
            Set.of(EndpointCapability.RECEIVE));
        var core = coreWithDescriptors(List.of(desc), "orders");
        core.processMessage(new byte[0], "orders", null);
        assertThat(captured.get(0).getDataContentType()).isEqualTo("application/json");
    }

    @Test
    void processMessage_withoutContentType_omitsDataContentType() {
        var core = coreWithDescriptors(List.of(descriptor("orders", "io.casehub.orders.placed")), "orders");
        core.processMessage(new byte[0], "orders", null);
        assertThat(captured.get(0).getDataContentType()).isNull();
    }

    @Test
    void processMessage_nullDescriptor_omitsDataContentType() {
        var core = coreWithDescriptors(List.of(), "");
        core.processMessage(new byte[0], "unknown", null);
        assertThat(captured.get(0).getDataContentType()).isNull();
    }

    @Test
    void processMessage_data_contains_raw_bytes() {
        byte[] payload = "test".getBytes(StandardCharsets.UTF_8);
        var core = coreWithDescriptors(List.of(descriptor("orders", "io.casehub.orders.placed")), "orders");
        core.processMessage(payload, "orders", null);
        assertThat(new String(captured.get(0).getData().toBytes(), StandardCharsets.UTF_8)).isEqualTo("test");
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
