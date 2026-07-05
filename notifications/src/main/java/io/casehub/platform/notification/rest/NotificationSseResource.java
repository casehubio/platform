package io.casehub.platform.notification.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.notification.AllNotificationsRead;
import io.casehub.platform.api.notification.NotificationCreated;
import io.casehub.platform.api.notification.NotificationStatusChanged;
import io.casehub.platform.api.notification.ReactiveNotificationStore;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-Sent Events endpoint for real-time notification push.
 *
 * <p>Maintains a connection map keyed by {@code tenancyId::userId}. Each user can have
 * multiple concurrent connections (multiple tabs/devices).
 *
 * <p>Principal capture: {@code userId} and {@code tenancyId} are captured from
 * {@link CurrentPrincipal} during stream establishment and stored alongside the emitter.
 * {@code @ObservesAsync} CDI event handlers run on managed executor threads where no
 * request context is active — they match against stored credentials, never against
 * {@code CurrentPrincipal}.
 *
 * <p>Event types pushed to client:
 * <ul>
 *   <li>{@code unread-count} — badge count (on connect, after status change, after mark-all-read)
 *   <li>{@code notification} — full {@code Notification} JSON (on store)
 *   <li>{@code notification-updated} — full {@code Notification} JSON with new status (on markRead/dismiss)
 * </ul>
 */
@ApplicationScoped
@Path("/notifications/stream")
public class NotificationSseResource {

    private static final Logger LOG = Logger.getLogger(NotificationSseResource.class);

    private final ConcurrentHashMap<String, Set<EmitterWithContext>> connections = new ConcurrentHashMap<>();
    private final ReactiveNotificationStore store;
    private final CurrentPrincipal principal;
    private final ObjectMapper objectMapper;

    @Inject
    public NotificationSseResource(
            ReactiveNotificationStore store,
            CurrentPrincipal principal,
            ObjectMapper objectMapper) {
        this.store = store;
        this.principal = principal;
        this.objectMapper = objectMapper;
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void stream(@Context SseEventSink eventSink, @Context Sse sse) {
        // Capture principal at stream establishment (request context active here)
        String userId = principal.actorId();
        String tenancyId = principal.tenancyId();
        String key = tenancyId + "::" + userId;

        var emitterWithContext = new EmitterWithContext(eventSink, sse, userId, tenancyId);

        // Register emitter
        connections.computeIfAbsent(key, k ->
            Collections.newSetFromMap(new ConcurrentHashMap<>())
        ).add(emitterWithContext);

        // Send initial unread count
        store.unreadCount(userId, tenancyId)
            .subscribe().with(
                count -> sendUnreadCount(eventSink, sse, count),
                error -> LOG.errorf(error, "Failed to fetch initial unread count for user %s", userId)
            );
    }

    void onNotificationCreated(@ObservesAsync NotificationCreated event) {
        var notification = event.notification();
        String key = notification.tenancyId() + "::" + notification.userId();

        var emitters = connections.get(key);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(notification);
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "Failed to serialize notification %s", notification.id());
            return;
        }

        for (var emitter : emitters) {
            if (!emitter.eventSink().isClosed()) {
                try {
                    var event2 = emitter.sse().newEventBuilder()
                        .name("notification")
                        .data(json)
                        .build();
                    emitter.eventSink().send(event2);
                } catch (Exception e) {
                    LOG.debugf(e, "Failed to send notification to user %s", notification.userId());
                    // Remove closed emitter
                    removeEmitter(key, emitter);
                }
            } else {
                removeEmitter(key, emitter);
            }
        }

        // Send updated unread count
        store.unreadCount(notification.userId(), notification.tenancyId())
            .subscribe().with(
                count -> sendUnreadCountToUser(notification.userId(), notification.tenancyId(), count),
                error -> LOG.errorf(error, "Failed to fetch unread count for user %s", notification.userId())
            );
    }

    void onNotificationStatusChanged(@ObservesAsync NotificationStatusChanged event) {
        var notification = event.notification();
        String key = notification.tenancyId() + "::" + notification.userId();

        var emitters = connections.get(key);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(notification);
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "Failed to serialize notification %s", notification.id());
            return;
        }

        for (var emitter : emitters) {
            if (!emitter.eventSink().isClosed()) {
                try {
                    var event2 = emitter.sse().newEventBuilder()
                        .name("notification-updated")
                        .data(json)
                        .build();
                    emitter.eventSink().send(event2);
                } catch (Exception e) {
                    LOG.debugf(e, "Failed to send updated notification to user %s", notification.userId());
                    removeEmitter(key, emitter);
                }
            } else {
                removeEmitter(key, emitter);
            }
        }

        // Send updated unread count
        store.unreadCount(notification.userId(), notification.tenancyId())
            .subscribe().with(
                count -> sendUnreadCountToUser(notification.userId(), notification.tenancyId(), count),
                error -> LOG.errorf(error, "Failed to fetch unread count for user %s", notification.userId())
            );
    }

    void onAllNotificationsRead(@ObservesAsync AllNotificationsRead event) {
        // Query actual unread count (don't assume zero)
        store.unreadCount(event.userId(), event.tenancyId())
            .subscribe().with(
                count -> sendUnreadCountToUser(event.userId(), event.tenancyId(), count),
                error -> LOG.errorf(error, "Failed to fetch unread count for user %s", event.userId())
            );
    }

    private void sendUnreadCountToUser(String userId, String tenancyId, long count) {
        String key = tenancyId + "::" + userId;
        var emitters = connections.get(key);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (var emitter : emitters) {
            if (!emitter.eventSink().isClosed()) {
                try {
                    var event = emitter.sse().newEventBuilder()
                        .name("unread-count")
                        .data("{\"count\":" + count + "}")
                        .build();
                    emitter.eventSink().send(event);
                } catch (Exception e) {
                    LOG.debugf(e, "Failed to send unread count to user %s", userId);
                    removeEmitter(key, emitter);
                }
            } else {
                removeEmitter(key, emitter);
            }
        }
    }

    private void sendUnreadCount(SseEventSink eventSink, Sse sse, long count) {
        try {
            var event = sse.newEventBuilder()
                .name("unread-count")
                .data("{\"count\":" + count + "}")
                .build();
            eventSink.send(event);
        } catch (Exception e) {
            LOG.debug("Failed to send initial unread count", e);
        }
    }

    @Scheduled(every = "60s")
    void sweepStaleEmitters() {
        connections.forEach((key, emitters) ->
            emitters.removeIf(e -> e.eventSink().isClosed()));
        connections.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /**
     * Removes an emitter from the connection map and cleans up the outer map key
     * when the set becomes empty.
     */
    private void removeEmitter(String connectionKey, EmitterWithContext emitter) {
        connections.computeIfPresent(connectionKey, (key, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    private record EmitterWithContext(
        SseEventSink eventSink,
        Sse sse,
        String userId,
        String tenancyId
    ) {}
}
