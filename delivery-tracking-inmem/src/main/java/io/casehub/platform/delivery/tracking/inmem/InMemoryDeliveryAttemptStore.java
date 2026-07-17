package io.casehub.platform.delivery.tracking.inmem;

import io.casehub.platform.api.delivery.DeliveryAttempt;
import io.casehub.platform.api.delivery.DeliveryAttemptPage;
import io.casehub.platform.api.delivery.DeliveryAttemptQuery;
import io.casehub.platform.api.delivery.DeliveryAttemptStore;
import io.casehub.platform.api.delivery.DeliveryStatus;
import io.casehub.platform.api.delivery.EngagementEvent;
import io.casehub.platform.api.delivery.EngagementType;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Alternative
@Priority(100)
@ApplicationScoped
public class InMemoryDeliveryAttemptStore implements DeliveryAttemptStore {

    private static final Logger   LOG           = Logger.getLogger(InMemoryDeliveryAttemptStore.class);
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, DeliveryAttempt>       store           = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<EngagementEvent>> engagementStore = new ConcurrentHashMap<>();

    private final int maxSize;

    public InMemoryDeliveryAttemptStore(
            @ConfigProperty(name = "casehub.delivery.tracking.inmem.max-size", defaultValue = "10000")
            int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public void store(DeliveryAttempt attempt) {
        store.put(attempt.id(), attempt);
        evictIfNeeded();
    }

    @Override
    public void update(DeliveryAttempt attempt) {
        store.put(attempt.id(), attempt);
    }

    @Override
    public DeliveryAttempt findById(String id) {
        return store.get(id);
    }


    @Override
    public synchronized List<DeliveryAttempt> claimRetryable(Instant now, int batchSize) {
        List<DeliveryAttempt> eligible = store.values().stream()
                                              .filter(a -> a.status() == DeliveryStatus.RETRYING)
                                              .filter(a -> a.nextRetryAt() != null)
                                              .filter(a -> !a.nextRetryAt().isAfter(now))
                                              .sorted(Comparator.comparing(DeliveryAttempt::nextRetryAt))
                                              .limit(batchSize)
                                              .toList();

        Instant               claimExpiry = now.plus(CLAIM_TIMEOUT);
        List<DeliveryAttempt> claimed     = new ArrayList<>(eligible.size());
        for (DeliveryAttempt a : eligible) {
            var advanced = new DeliveryAttempt(
                    a.id(), a.notificationId(), a.channelId(), a.userId(), a.tenancyId(),
                    a.deliveryType(), a.status(), a.attemptCount(),
                    a.createdAt(), a.lastAttemptedAt(), a.deliveredAt(),
                    claimExpiry, a.failureReason(), a.payload(),
                    a.firstOpenedAt(), a.firstClickedAt());
            store.put(a.id(), advanced);
            claimed.add(a);
        }
        return claimed;
    }

    @Override
    public DeliveryAttemptPage find(DeliveryAttemptQuery query) {
        List<DeliveryAttempt> filtered = store.values().stream()
                                              .filter(a -> a.tenancyId().equals(query.tenancyId()))
                                              .filter(a -> query.userId() == null || a.userId().equals(query.userId()))
                                              .filter(a -> query.channelId() == null || a.channelId().equals(query.channelId()))
                                              .filter(a -> query.status() == null || a.status() == query.status())
                                              .sorted(Comparator.comparing(DeliveryAttempt::createdAt).reversed()
                                                                .thenComparing(Comparator.comparing(DeliveryAttempt::id).reversed()))
                                              .toList();

        int offset = 0;
        if (query.cursor() != null) {
            offset = Integer.parseInt(query.cursor());
        }

        int                   end        = Math.min(offset + query.limit(), filtered.size());
        List<DeliveryAttempt> page       = filtered.subList(offset, end);
        String                nextCursor = end < filtered.size() ? String.valueOf(end) : null;

        return new DeliveryAttemptPage(page, nextCursor);
    }

    @Override
    public List<DeliveryAttempt> findByNotificationId(String notificationId) {
        return store.values().stream()
                    .filter(a -> notificationId.equals(a.notificationId()))
                    .sorted(Comparator.comparing(DeliveryAttempt::createdAt))
                    .toList();
    }

    private void evictIfNeeded() {
        if (maxSize <= 0 || store.size() <= maxSize) {
            return;
        }
        store.values().stream()
             .sorted(Comparator.comparing(DeliveryAttempt::createdAt))
             .limit(store.size() - maxSize)
             .forEach(a -> {
                 store.remove(a.id());
                 engagementStore.remove(a.id());
                 LOG.debugf("Evicted delivery attempt %s — max size %d exceeded", a.id(), maxSize);
             });
    }

    @Override
    public void recordEngagement(EngagementEvent event) {
        engagementStore.computeIfAbsent(event.attemptId(), k -> new CopyOnWriteArrayList<>()).add(event);
        store.computeIfPresent(event.attemptId(), (id, attempt) -> {
            Instant firstOpened  = attempt.firstOpenedAt();
            Instant firstClicked = attempt.firstClickedAt();
            if (event.type() == EngagementType.OPENED && firstOpened == null) {
                firstOpened = event.recordedAt();
            }
            if (event.type() == EngagementType.CLICKED && firstClicked == null) {
                firstClicked = event.recordedAt();
            }
            if (firstOpened == attempt.firstOpenedAt() && firstClicked == attempt.firstClickedAt()) {
                return attempt;
            }
            return new DeliveryAttempt(
                    attempt.id(), attempt.notificationId(), attempt.channelId(),
                    attempt.userId(), attempt.tenancyId(), attempt.deliveryType(),
                    attempt.status(), attempt.attemptCount(),
                    attempt.createdAt(), attempt.lastAttemptedAt(), attempt.deliveredAt(),
                    attempt.nextRetryAt(), attempt.failureReason(), attempt.payload(),
                    firstOpened, firstClicked);
        });
    }

    @Override
    public List<EngagementEvent> findEngagementsByAttemptId(String attemptId) {
        return List.copyOf(engagementStore.getOrDefault(attemptId, List.of()));
    }

    @Override
    public List<EngagementEvent> findEngagementsByNotificationId(String notificationId) {
        return engagementStore.values().stream()
                              .flatMap(List::stream)
                              .filter(e -> notificationId.equals(e.notificationId()))
                              .sorted(Comparator.comparing(EngagementEvent::recordedAt))
                              .toList();
    }
}
