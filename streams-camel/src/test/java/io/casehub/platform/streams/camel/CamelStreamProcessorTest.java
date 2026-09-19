package io.casehub.platform.streams.camel;

import io.casehub.platform.api.endpoints.EndpointCapability;
import io.casehub.platform.api.endpoints.EndpointDescriptor;
import io.casehub.platform.api.endpoints.EndpointPropertyKeys;
import io.casehub.platform.api.endpoints.EndpointProtocol;
import io.casehub.platform.api.endpoints.EndpointType;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.streams.StreamCloudEventFactory;
import io.casehub.platform.streams.StreamConstants;
import io.cloudevents.CloudEvent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CamelStreamProcessorTest {

    private EndpointDescriptor descriptor(String uri, String streamType) {
        return new EndpointDescriptor(
            Path.of("streams", "camel-data"),
            TenancyConstants.DEFAULT_TENANT_ID,
            EndpointType.WORKER,
            EndpointProtocol.CAMEL,
            Map.of(EndpointPropertyKeys.URL, uri,
                   EndpointPropertyKeys.STREAM_EVENT_TYPE, streamType),
            null,
            Set.of(EndpointCapability.RECEIVE));
    }

    @Test
    void cloud_event_type_from_descriptor() {
        var desc = descriptor("direct:test", "io.casehub.camel.event");
        CloudEvent ce = StreamCloudEventFactory.build(new byte[0], desc, null,
            URI.create("/platform/streams/camel"), StreamConstants.UNREGISTERED_TYPE);
        assertThat(ce.getType()).isEqualTo("io.casehub.camel.event");
    }

    @Test
    void cloud_event_tenancyid_from_descriptor() {
        var desc = descriptor("direct:test", "io.casehub.camel.event");
        CloudEvent ce = StreamCloudEventFactory.build(new byte[0], desc, null,
            URI.create("/platform/streams/camel"), StreamConstants.UNREGISTERED_TYPE);
        assertThat(ce.getExtension("tenancyid")).isEqualTo(TenancyConstants.DEFAULT_TENANT_ID);
    }

    @Test
    void cloud_event_source_contains_camel() {
        var desc = descriptor("direct:test", "io.casehub.camel.event");
        CloudEvent ce = StreamCloudEventFactory.build(new byte[0], desc, null,
            URI.create("/platform/streams/camel"), StreamConstants.UNREGISTERED_TYPE);
        assertThat(ce.getSource().toString()).contains("camel");
    }

    @Test
    void cloud_event_with_content_type() {
        EndpointDescriptor desc = new EndpointDescriptor(
            Path.of("streams", "camel-data"),
            TenancyConstants.DEFAULT_TENANT_ID,
            EndpointType.WORKER,
            EndpointProtocol.CAMEL,
            Map.of(EndpointPropertyKeys.URL, "direct:test",
                   EndpointPropertyKeys.STREAM_EVENT_TYPE, "io.casehub.camel.event",
                   EndpointPropertyKeys.STREAM_DATA_CONTENT_TYPE, "application/json"),
            null,
            Set.of(EndpointCapability.RECEIVE));
        CloudEvent ce = StreamCloudEventFactory.build(new byte[0], desc, null,
            URI.create("/platform/streams/camel"), StreamConstants.UNREGISTERED_TYPE);
        assertThat(ce.getDataContentType()).isEqualTo("application/json");
    }

    @Test
    void cloud_event_data_is_raw_bytes() {
        byte[] payload = "payload".getBytes();
        var desc = descriptor("direct:test", "io.casehub.camel.event");
        CloudEvent ce = StreamCloudEventFactory.build(payload, desc, null,
            URI.create("/platform/streams/camel"), StreamConstants.UNREGISTERED_TYPE);
        assertThat(ce.getData().toBytes()).isEqualTo(payload);
    }
}
