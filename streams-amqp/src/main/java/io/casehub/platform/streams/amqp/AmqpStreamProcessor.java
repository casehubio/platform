package io.casehub.platform.streams.amqp;

import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.cloudevents.CloudEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.reactive.messaging.amqp.IncomingAmqpMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * AMQP stream ingestion processor.
 *
 * <p>Receives messages on a single static {@code @Incoming("casehub-amqp-stream")} channel.
 * Always receives as {@code Message<byte[]>} (P0 — no native CloudEvents deserialization).
 * Builds a CloudEvent from scratch and fires {@code Event<CloudEvent>.fireAsync()}.
 *
 * <p>Does NOT observe {@link io.casehub.platform.api.endpoints.EndpointRegistered} —
 * AMQP descriptors must be registered before application startup via
 * {@code endpoints-config} YAML. For runtime-dynamic queues or multi-queue fan-in,
 * use {@code streams-camel}.
 *
 * <p>SmallRye AMQP does NOT support plural addresses per channel (unlike Kafka's
 * {@code topics=a,b}). Each channel has exactly one address. For multi-queue fan-in
 * use {@code streams-camel}.
 */
@ApplicationScoped
public class AmqpStreamProcessor {

    private static final Logger LOG = Logger.getLogger(AmqpStreamProcessor.class);
    private static final String CHANNEL_NAME = "casehub-amqp-stream";

    @Inject
    EndpointRegistry endpointRegistry;

    @Inject
    Event<CloudEvent> cloudEventBus;

    private AmqpStreamProcessorCore core;

    void onStartup(@Observes StartupEvent ev) {
        String address = ConfigProvider.getConfig()
            .getOptionalValue("mp.messaging.incoming." + CHANNEL_NAME + ".address", String.class)
            .orElse("");

        core = new AmqpStreamProcessorCore(endpointRegistry,
            ce -> cloudEventBus.fireAsync(ce)
                .whenComplete((e, t) -> {
                    if (t != null) LOG.warnf(t, "CloudEvent observer failed");
                }),
            address.isBlank() ? Map.of() : Map.of(CHANNEL_NAME, address));
        core.init();
    }

    @Incoming(CHANNEL_NAME)
    public CompletionStage<Void> process(Message<byte[]> message) {
        Optional<IncomingAmqpMetadata> meta = message.getMetadata(IncomingAmqpMetadata.class);

        String address = meta.map(IncomingAmqpMetadata::getAddress).orElse("unknown");

        String tenancyId = meta.map(m -> {
            var props = m.getProperties();
            return props != null ? props.getString("X-Tenancy-ID") : null;
        }).orElse(null);

        core.processMessage(message.getPayload(), address, tenancyId);
        return message.ack();
    }
}
