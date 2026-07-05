package io.casehub.platform.api.delivery;

import io.casehub.platform.api.notification.NotificationInput;

/**
 * SPI for delivering notifications via a specific channel.
 *
 * <p>Each deliverer self-registers its {@link DeliveryChannelDescriptor} in the
 * {@link DeliveryChannelRegistry} at {@code @PostConstruct}.
 */
public interface NotificationDeliverer {

    /**
     * Channel identifier this deliverer handles.
     *
     * @return channel identifier
     */
    String channelId();

    /**
     * Deliver a notification via this channel.
     *
     * @param notification notification to deliver
     * @return delivery result
     */
    DeliveryResult deliver(NotificationInput notification);
}
