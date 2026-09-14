package io.casehub.platform.notification.rest;

import io.casehub.platform.api.delivery.DeliveryChannelApi;
import io.casehub.platform.api.delivery.DeliveryChannelDescriptor;
import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

@ApplicationScoped
public class DeliveryChannelService implements DeliveryChannelApi {

    private final DeliveryChannelRegistry channelRegistry;

    @Inject
    public DeliveryChannelService(DeliveryChannelRegistry channelRegistry) {
        this.channelRegistry = channelRegistry;
    }

    @Override
    public Set<DeliveryChannelDescriptor> listChannels() {
        return channelRegistry.discover();
    }
}
