package io.casehub.platform.streams;

import io.casehub.platform.api.endpoints.EndpointDescriptor;
import io.casehub.platform.api.endpoints.EndpointPropertyKeys;
import io.casehub.platform.api.identity.TenancyConstants;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class StreamCloudEventFactory {

    private StreamCloudEventFactory() {}

    public static CloudEvent build(byte[] data, EndpointDescriptor descriptor,
                                   String tenancyIdOverride, URI source,
                                   String fallbackType) {
        String type = descriptor != null
            ? descriptor.properties().getOrDefault(EndpointPropertyKeys.STREAM_EVENT_TYPE,
                fallbackType)
            : fallbackType;

        String effectiveTenancyId = tenancyIdOverride != null
            ? tenancyIdOverride
            : (descriptor != null ? descriptor.tenancyId() : TenancyConstants.DEFAULT_TENANT_ID);

        CloudEventBuilder builder = CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withType(type)
            .withSource(source)
            .withTime(OffsetDateTime.now())
            .withData(data)
            .withExtension("tenancyid", effectiveTenancyId);

        if (descriptor != null) {
            String contentType = descriptor.properties()
                .get(EndpointPropertyKeys.STREAM_DATA_CONTENT_TYPE);
            if (contentType != null) {
                builder = builder.withDataContentType(contentType);
            }
        }

        return builder.build();
    }
}
