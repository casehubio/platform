package io.casehub.platform.delivery;

import io.casehub.platform.api.delivery.DeliveryChannelDescriptor;
import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import io.casehub.platform.api.delivery.NotificationDeliverer;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.Set;

/**
 * No-op {@link DeliveryChannelRegistry} — active when no backend module is on the classpath.
 *
 * <p>All queries return empty. {@link #register(DeliveryChannelDescriptor, NotificationDeliverer)}
 * is a silent no-op. Does NOT fire CDI events per protocol — no-op implementations must not
 * fire events.
 *
 * <p>Displaced by any {@code @Alternative} or bare {@code @ApplicationScoped}
 * {@link DeliveryChannelRegistry} implementation on the classpath, per the
 * {@code @DefaultBean} CDI displacement contract.
 */
@DefaultBean
@ApplicationScoped
public class NoOpDeliveryChannelRegistry implements DeliveryChannelRegistry {

    @Override
    public void register(final DeliveryChannelDescriptor descriptor, final NotificationDeliverer deliverer) {
        // Silent no-op — does NOT fire CDI events
    }

    @Override
    public Optional<DeliveryChannelDescriptor> resolve(final String channelId) {
        return Optional.empty();
    }

    @Override
    public Optional<NotificationDeliverer> resolveDeliverer(final String channelId) {
        return Optional.empty();
    }

    @Override
    public Set<DeliveryChannelDescriptor> discover() {
        return Set.of();
    }
}
