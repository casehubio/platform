package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.delivery.DeliveryChannelDescriptor;
import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import io.casehub.platform.api.delivery.DeliveryChannels;
import io.casehub.platform.api.delivery.DeliveryResult;
import io.casehub.platform.api.delivery.NotificationDeliverer;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationStore;

import io.quarkus.arc.Unremovable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * In-app notification deliverer — stores notifications in the {@link NotificationStore}.
 *
 * <p>Self-registers with the {@link DeliveryChannelRegistry} at startup.
 * In-app is an internal channel (not external), so it is never suppressed
 * by snooze or quiet hours — only mute drops it entirely.
 */
@Unremovable
@ApplicationScoped
public class InAppNotificationDeliverer implements NotificationDeliverer {

    private static final Logger LOG = Logger.getLogger(InAppNotificationDeliverer.class);

    private final NotificationStore notificationStore;
    private final DeliveryChannelRegistry channelRegistry;

    @Inject
    public InAppNotificationDeliverer(final NotificationStore notificationStore,
                                      final DeliveryChannelRegistry channelRegistry) {
        this.notificationStore = notificationStore;
        this.channelRegistry = channelRegistry;
    }

    @PostConstruct
    void register() {
        channelRegistry.register(
                new DeliveryChannelDescriptor(
                        DeliveryChannels.IN_APP,
                        "In-App Inbox",
                        false,
                        true,
                        NotificationSeverity.INFO,
                        null),
                this);
    }

    @Override
    public String channelId() {
        return DeliveryChannels.IN_APP;
    }

    @Override
    public DeliveryResult deliver(final NotificationInput notification) {
        try {
            notificationStore.store(notification);
            return new DeliveryResult(true, null);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to store in-app notification for user '%s'", notification.userId());
            return new DeliveryResult(false, e.getMessage());
        }
    }
}
