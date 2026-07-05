package io.casehub.platform.api.delivery;

import java.util.Optional;
import java.util.Set;

/**
 * Registry of delivery channels. Each {@link NotificationDeliverer} self-registers
 * its {@link DeliveryChannelDescriptor} at {@code @PostConstruct}.
 *
 * <p>Registration is atomic: descriptor and deliverer are stored together.
 * Follows the {@link io.casehub.platform.api.datasource.DataSourceRegistry}
 * precedent where metadata + instance are combined in one registry.
 */
public interface DeliveryChannelRegistry {

    /**
     * Register a channel with its deliverer. Atomic — descriptor and deliverer
     * are stored together. Called from deliverer {@code @PostConstruct}.
     *
     * @param descriptor channel metadata
     * @param deliverer  deliverer instance
     */
    void register(DeliveryChannelDescriptor descriptor, NotificationDeliverer deliverer);

    /**
     * Resolve a channel descriptor by channelId.
     *
     * @param channelId channel identifier
     * @return descriptor if registered, empty otherwise
     */
    Optional<DeliveryChannelDescriptor> resolve(String channelId);

    /**
     * Resolve a deliverer instance by channelId.
     *
     * @param channelId channel identifier
     * @return deliverer if registered, empty otherwise
     */
    Optional<NotificationDeliverer> resolveDeliverer(String channelId);

    /**
     * Discover all registered channels.
     *
     * @return set of registered channel descriptors
     */
    Set<DeliveryChannelDescriptor> discover();
}
