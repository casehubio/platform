package io.casehub.platform.streams;

import io.casehub.platform.api.endpoints.EndpointCapability;
import io.casehub.platform.api.endpoints.EndpointDescriptor;
import io.casehub.platform.api.endpoints.EndpointPropertyKeys;
import io.casehub.platform.api.endpoints.EndpointProtocol;
import io.casehub.platform.api.endpoints.EndpointType;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.path.Path;
import io.cloudevents.CloudEvent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StreamCloudEventFactoryTest {

    private static final URI SOURCE = URI.create("/platform/streams/test/my-topic");

    private EndpointDescriptor descriptor(String eventType, String tenancyId, String contentType) {
        var props = new HashMap<>(Map.of(
            EndpointPropertyKeys.STREAM_EVENT_TYPE, eventType));
        if (contentType != null) {
            props.put(EndpointPropertyKeys.STREAM_DATA_CONTENT_TYPE, contentType);
        }
        return new EndpointDescriptor(Path.of("/test"), tenancyId, EndpointType.SYSTEM,
            EndpointProtocol.KAFKA, props, null, Set.of(EndpointCapability.RECEIVE));
    }

    @Test
    void builds_cloud_event_with_descriptor() {
        byte[] data = "hello".getBytes();
        var desc = descriptor("order.created", "tenant-1", "application/json");

        CloudEvent ce = StreamCloudEventFactory.build(data, desc, null, SOURCE,
            StreamConstants.UNREGISTERED_TYPE);

        assertThat(ce.getId()).isNotNull();
        assertThat(ce.getType()).isEqualTo("order.created");
        assertThat(ce.getSource()).isEqualTo(SOURCE);
        assertThat(ce.getTime()).isNotNull();
        assertThat(ce.getData().toBytes()).isEqualTo(data);
        assertThat(ce.getExtension("tenancyid")).isEqualTo("tenant-1");
        assertThat(ce.getDataContentType()).isEqualTo("application/json");
    }

    @Test
    void tenancy_id_override_takes_precedence() {
        var desc = descriptor("evt", "descriptor-tenant", null);

        CloudEvent ce = StreamCloudEventFactory.build("x".getBytes(), desc, "override-tenant",
            SOURCE, StreamConstants.UNREGISTERED_TYPE);

        assertThat(ce.getExtension("tenancyid")).isEqualTo("override-tenant");
    }

    @Test
    void null_descriptor_uses_fallback_type_and_default_tenant() {
        CloudEvent ce = StreamCloudEventFactory.build("x".getBytes(), null, null,
            SOURCE, "io.casehub.platform.streams.kafka.unregistered");

        assertThat(ce.getType()).isEqualTo("io.casehub.platform.streams.kafka.unregistered");
        assertThat(ce.getExtension("tenancyid")).isEqualTo(TenancyConstants.DEFAULT_TENANT_ID);
        assertThat(ce.getDataContentType()).isNull();
    }

    @Test
    void null_descriptor_with_tenancy_override() {
        CloudEvent ce = StreamCloudEventFactory.build("x".getBytes(), null, "my-tenant",
            SOURCE, StreamConstants.UNREGISTERED_TYPE);

        assertThat(ce.getExtension("tenancyid")).isEqualTo("my-tenant");
    }

    @Test
    void no_content_type_when_not_on_descriptor() {
        var desc = descriptor("evt", "t", null);

        CloudEvent ce = StreamCloudEventFactory.build("x".getBytes(), desc, null,
            SOURCE, StreamConstants.UNREGISTERED_TYPE);

        assertThat(ce.getDataContentType()).isNull();
    }
}
