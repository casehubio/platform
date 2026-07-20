package io.casehub.platform.api.delivery;

import java.util.Optional;

/**
 * SPI for resolving a user's delivery destination for a specific channel.
 *
 * <p>Implementations are {@code @ApplicationScoped} CDI beans discovered automatically,
 * one per channel type. The bridge matches resolvers to deliverers by {@link #channelId()}.
 *
 * <p>Resolution models vary by channel:
 * <ul>
 *   <li><b>Per-user</b> (email, SMS, WhatsApp): resolves to the user's contact attribute</li>
 *   <li><b>Per-tenant</b> (future — Slack, Teams): resolves to a shared webhook URL</li>
 * </ul>
 */
public interface DestinationResolver {

    /**
     * Channel type this resolver handles.
     *
     * @return channel type identifier; never null or blank
     */
    String channelId();

    /**
     * Resolve the delivery destination for a user on this channel.
     *
     * @param userId    notification recipient
     * @param tenancyId tenant isolation
     * @return destination string if known, empty otherwise
     */
    Optional<String> resolve(String userId, String tenancyId);
}
