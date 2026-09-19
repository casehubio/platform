package io.casehub.platform.streams.kafka;

import io.casehub.platform.api.endpoints.EndpointCapability;
import io.casehub.platform.api.endpoints.EndpointDescriptor;
import io.casehub.platform.api.endpoints.EndpointPropertyKeys;
import io.casehub.platform.api.endpoints.EndpointProtocol;
import io.casehub.platform.api.endpoints.EndpointQuery;
import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.streams.StreamCloudEventFactory;
import io.cloudevents.CloudEvent;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class KafkaStreamProcessorCore {

    private static final Logger LOG = Logger.getLogger(KafkaStreamProcessorCore.class.getName());
    private static final String FALLBACK_TYPE = "io.casehub.platform.streams.kafka.unregistered";

    private final EndpointRegistry endpointRegistry;
    private final Consumer<CloudEvent> eventCallback;
    private final Map<String, String> channelTopicConfig;
    private final Map<String, EndpointDescriptor> topicToDescriptor = new HashMap<>();

    public KafkaStreamProcessorCore(EndpointRegistry endpointRegistry,
                                    Consumer<CloudEvent> eventCallback,
                                    Map<String, String> channelTopicConfig) {
        this.endpointRegistry = endpointRegistry;
        this.eventCallback = eventCallback;
        this.channelTopicConfig = channelTopicConfig;
    }

    public void init() {
        String topicConfig = channelTopicConfig.values().stream().findFirst().orElse("");
        if (topicConfig.isBlank()) {
            LOG.warning("No topic configured — no KAFKA streams will be processed");
            return;
        }

        var descriptors = endpointRegistry.discover(
            new EndpointQuery(TenancyConstants.DEFAULT_TENANT_ID, null,
                EndpointProtocol.KAFKA, Set.of(EndpointCapability.RECEIVE)));

        for (String raw : topicConfig.split(",")) {
            String topic = raw.strip();
            if (topic.isBlank()) continue;
            descriptors.stream()
                .filter(d -> topic.equals(d.properties().get(EndpointPropertyKeys.TOPIC)))
                .findFirst()
                .ifPresentOrElse(
                    d -> topicToDescriptor.put(topic, d),
                    () -> LOG.warning("No EndpointDescriptor found for Kafka topic '" + topic + "'"));
        }
    }

    public void processMessage(byte[] body, String topic, String tenancyId) {
        EndpointDescriptor descriptor = topicToDescriptor.get(topic);
        URI source = URI.create("/platform/streams/kafka/" + topic);
        CloudEvent ce = StreamCloudEventFactory.build(body, descriptor, tenancyId, source, FALLBACK_TYPE);
        eventCallback.accept(ce);
    }
}
