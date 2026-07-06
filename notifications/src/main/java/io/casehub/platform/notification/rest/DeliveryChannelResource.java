package io.casehub.platform.notification.rest;

import io.casehub.platform.api.delivery.DeliveryChannelDescriptor;
import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.Set;

/**
 * REST endpoints for delivery channel discovery.
 *
 * <p>Returns all registered delivery channels. Each {@link io.casehub.platform.api.delivery.NotificationDeliverer}
 * self-registers its descriptor at {@code @PostConstruct}.
 */
@ApplicationScoped
@Path("/notifications/channels")
public class DeliveryChannelResource {

    private final DeliveryChannelRegistry channelRegistry;

    @Inject
    public DeliveryChannelResource(DeliveryChannelRegistry channelRegistry) {
        this.channelRegistry = channelRegistry;
    }

    /**
     * Get all registered delivery channels.
     *
     * @return set of channel descriptors
     */
    @GET
    public Set<DeliveryChannelDescriptor> listChannels() {
        return channelRegistry.discover();
    }
}
