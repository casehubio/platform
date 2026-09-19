package io.casehub.platform.streams.kafka;

import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.cloudevents.CloudEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.Header;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class KafkaStreamProcessor {

    private static final Logger LOG = Logger.getLogger(KafkaStreamProcessor.class);
    private static final String CHANNEL_NAME = "casehub-kafka-stream";

    @ConfigProperty(name = "casehub.streams.kafka.channel", defaultValue = "casehub-kafka-stream")
    String channelName;

    @Inject
    EndpointRegistry endpointRegistry;

    @Inject
    Event<CloudEvent> cloudEventBus;

    private KafkaStreamProcessorCore core;

    void onStartup(@Observes StartupEvent ev) {
        String topicConfig = ConfigProvider.getConfig()
            .getOptionalValue("mp.messaging.incoming." + channelName + ".topic", String.class)
            .or(() -> ConfigProvider.getConfig()
                .getOptionalValue("mp.messaging.incoming." + channelName + ".topics", String.class))
            .orElse("");

        core = new KafkaStreamProcessorCore(endpointRegistry,
            ce -> cloudEventBus.fireAsync(ce)
                .whenComplete((e, t) -> {
                    if (t != null) LOG.warnf(t, "CloudEvent observer failed");
                }),
            topicConfig.isBlank() ? Map.of() : Map.of(channelName, topicConfig));
        core.init();
    }

    @SuppressWarnings("unchecked")
    @Incoming(CHANNEL_NAME)
    public CompletionStage<Void> process(Message<byte[]> message) {
        @SuppressWarnings("rawtypes")
        Optional<IncomingKafkaRecordMetadata> rawMeta =
            message.getMetadata(IncomingKafkaRecordMetadata.class);
        Optional<IncomingKafkaRecordMetadata<?, byte[]>> meta =
            rawMeta.map(m -> (IncomingKafkaRecordMetadata<?, byte[]>) m);

        String topic = meta.map(IncomingKafkaRecordMetadata::getTopic).orElse("unknown");

        String tenancyId = meta.map(m -> {
            Header header = m.getHeaders().lastHeader("X-Tenancy-ID");
            return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
        }).orElse(null);

        core.processMessage(message.getPayload(), topic, tenancyId);
        return message.ack();
    }
}
