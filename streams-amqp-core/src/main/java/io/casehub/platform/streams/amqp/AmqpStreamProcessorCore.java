package io.casehub.platform.streams.amqp;

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

public class AmqpStreamProcessorCore {

    private static final Logger LOG = Logger.getLogger(AmqpStreamProcessorCore.class.getName());
    private static final String FALLBACK_TYPE = "io.casehub.platform.streams.amqp.unregistered";

    private final EndpointRegistry endpointRegistry;
    private final Consumer<CloudEvent> eventCallback;
    private final Map<String, String> channelAddressConfig;
    private final Map<String, EndpointDescriptor> addressToDescriptor = new HashMap<>();

    public AmqpStreamProcessorCore(EndpointRegistry endpointRegistry,
                                   Consumer<CloudEvent> eventCallback,
                                   Map<String, String> channelAddressConfig) {
        this.endpointRegistry = endpointRegistry;
        this.eventCallback = eventCallback;
        this.channelAddressConfig = channelAddressConfig;
    }

    public void init() {
        String address = channelAddressConfig.values().stream().findFirst().orElse("");
        if (address.isBlank()) {
            LOG.warning("No address configured — no AMQP streams will be processed");
            return;
        }

        var descriptors = endpointRegistry.discover(
            new EndpointQuery(TenancyConstants.DEFAULT_TENANT_ID, null,
                EndpointProtocol.AMQP, Set.of(EndpointCapability.RECEIVE)));

        descriptors.stream()
            .filter(d -> address.equals(d.properties().get(EndpointPropertyKeys.TOPIC)))
            .findFirst()
            .ifPresentOrElse(
                d -> addressToDescriptor.put(address, d),
                () -> LOG.warning("No EndpointDescriptor found for AMQP address '" + address + "'"));
    }

    public void processMessage(byte[] body, String address, String tenancyId) {
        EndpointDescriptor descriptor = addressToDescriptor.get(address);
        URI source = URI.create("/platform/streams/amqp/" + address);
        CloudEvent ce = StreamCloudEventFactory.build(body, descriptor, tenancyId, source, FALLBACK_TYPE);
        eventCallback.accept(ce);
    }
}
