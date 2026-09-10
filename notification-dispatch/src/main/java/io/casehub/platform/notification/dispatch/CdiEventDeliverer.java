package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.delivery.DeliveryResult;
import io.casehub.platform.api.delivery.NotificationDeliverer;
import io.casehub.platform.api.notification.NotificationInput;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

/**
 * Fire-and-forget notification delivery via CDI async event. Agents and systems observe
 * {@code @ObservesAsync NotificationInput} filtered by category.
 *
 * <p>No inbox persistence, no read/dismiss lifecycle. Used for agent signals, operational alerts,
 * and system events.
 */
@ApplicationScoped
public class CdiEventDeliverer implements NotificationDeliverer {

  public static final String CHANNEL_ID = "cdi-event";

  @Inject Event<NotificationInput> notificationEvents;

  @Override
  public String channelId() {
    return CHANNEL_ID;
  }

  @Override
  public DeliveryResult deliver(NotificationInput notification) {
    notificationEvents.fireAsync(notification);
    return new DeliveryResult(true, null);
  }
}
