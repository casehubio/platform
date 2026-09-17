package io.casehub.platform.subscription.spring.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionCreated;
import io.casehub.platform.api.subscription.SubscriptionDeleted;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionPage;
import io.casehub.platform.api.subscription.SubscriptionQuery;
import io.casehub.platform.api.subscription.SubscriptionScope;
import io.casehub.platform.api.subscription.SubscriptionStore;
import io.casehub.platform.api.subscription.SubscriptionUpdate;
import io.casehub.platform.api.subscription.SubscriptionUpdated;
import io.casehub.platform.subscription.jpa.SubscriptionEntity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class SpringSubscriptionStore implements SubscriptionStore {

    private final SubscriptionEntityRepository repo;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;

    public SpringSubscriptionStore(SubscriptionEntityRepository repo,
                                   ObjectMapper mapper,
                                   ApplicationEventPublisher events) {
        this.repo = repo;
        this.mapper = mapper;
        this.events = events;
    }

    @Override
    @Transactional
    public Subscription store(SubscriptionInput input) {
        SubscriptionEntity entity = SubscriptionEntity.fromInput(input, mapper);
        repo.saveAndFlush(entity);
        Subscription subscription = entity.toSubscription(mapper);
        events.publishEvent(new SubscriptionCreated(subscription));
        return subscription;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Subscription> findById(String id, String ownerId, String tenancyId) {
        return repo.findByIdAndTenancyIdAndOwnerOrSystem(id, tenancyId, ownerId)
                .map(entity -> entity.toSubscription(mapper));
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPage find(SubscriptionQuery query) {
        var effectiveScope = query.scope() != null ? query.scope() : SubscriptionScope.USER;
        int fetchLimit = query.limit() + 1;
        var pageable = PageRequest.of(0, fetchLimit);

        List<SubscriptionEntity> entities;
        if (query.cursor() != null) {
            CursorValue cursor = decodeCursor(query.cursor());
            if (cursor != null) {
                entities = findWithCursor(query, effectiveScope, cursor, pageable);
            } else {
                entities = findWithoutCursor(query, effectiveScope, pageable);
            }
        } else {
            entities = findWithoutCursor(query, effectiveScope, pageable);
        }

        boolean hasMore = entities.size() > query.limit();
        List<SubscriptionEntity> pageEntities = hasMore
                ? entities.subList(0, query.limit())
                : entities;

        List<Subscription> subscriptions = new ArrayList<>(pageEntities.size());
        for (SubscriptionEntity entity : pageEntities) {
            subscriptions.add(entity.toSubscription(mapper));
        }

        String nextCursor = null;
        if (hasMore && !pageEntities.isEmpty()) {
            SubscriptionEntity last = pageEntities.getLast();
            nextCursor = encodeCursor(last.createdAt, last.id);
        }
        return new SubscriptionPage(subscriptions, nextCursor);
    }

    private List<SubscriptionEntity> findWithoutCursor(SubscriptionQuery query,
                                                        SubscriptionScope scope,
                                                        PageRequest pageable) {
        if (scope == SubscriptionScope.SYSTEM) {
            if (query.enabled() != null) {
                return repo.findByTenantAndSystemAndEnabled(query.tenancyId(), query.enabled(), pageable);
            }
            return repo.findByTenantAndSystem(query.tenancyId(), pageable);
        } else {
            if (query.enabled() != null) {
                return repo.findByTenantAndOwnerAndEnabled(query.tenancyId(), query.ownerId(), query.enabled(), pageable);
            }
            return repo.findByTenantAndOwner(query.tenancyId(), query.ownerId(), pageable);
        }
    }

    private List<SubscriptionEntity> findWithCursor(SubscriptionQuery query,
                                                     SubscriptionScope scope,
                                                     CursorValue cursor,
                                                     PageRequest pageable) {
        if (scope == SubscriptionScope.SYSTEM) {
            return repo.findByTenantAndSystemAfterCursor(query.tenancyId(), cursor.createdAt, cursor.id, pageable);
        } else {
            return repo.findByTenantAndOwnerAfterCursor(query.tenancyId(), query.ownerId(), cursor.createdAt, cursor.id, pageable);
        }
    }

    @Override
    @Transactional
    public Optional<Subscription> update(String id, String ownerId, String tenancyId,
                                         SubscriptionUpdate update) {
        return repo.findByIdAndTenancyIdAndOwnerOrSystem(id, tenancyId, ownerId)
                .map(entity -> {
                    Subscription previous = entity.toSubscription(mapper);
                    applyUpdate(entity, update);
                    repo.save(entity);
                    Subscription updated = entity.toSubscription(mapper);
                    events.publishEvent(new SubscriptionUpdated(updated, previous));
                    return updated;
                });
    }

    @Override
    @Transactional
    public boolean delete(String id, String ownerId, String tenancyId) {
        return repo.findByIdAndTenancyIdAndOwnerOrSystem(id, tenancyId, ownerId)
                .map(entity -> {
                    Subscription subscription = entity.toSubscription(mapper);
                    repo.delete(entity);
                    events.publishEvent(new SubscriptionDeleted(subscription));
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Stream<Subscription> findAllEnabled() {
        return repo.findByEnabledTrue()
                .stream()
                .map(entity -> entity.toSubscription(mapper));
    }

    private void applyUpdate(SubscriptionEntity entity, SubscriptionUpdate update) {
        if (update.name() != null) {
            entity.name = update.name();
        }
        if (update.eventType() != null) {
            entity.eventType = update.eventType();
        }
        if (update.filters() != null) {
            entity.filtersJson = SubscriptionEntity.serializeFilters(update.filters(), mapper);
        }
        if (update.targets() != null) {
            entity.targetsJson = SubscriptionEntity.serializeTargets(update.targets(), mapper);
        }
        if (update.includeActor() != null) {
            entity.includeActor = update.includeActor();
        }
        if (update.template() != null) {
            entity.templateJson = SubscriptionEntity.serializeTemplate(update.template(), mapper);
        }
        if (update.enabled() != null) {
            entity.enabled = update.enabled();
        }
        entity.updatedAt = Instant.now();
    }

    private static String encodeCursor(Instant createdAt, String id) {
        String raw = createdAt.toEpochMilli() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());
    }

    private static CursorValue decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor));
            int sep = raw.indexOf('|');
            if (sep == -1) return null;
            long epochMillis = Long.parseLong(raw.substring(0, sep));
            String id = raw.substring(sep + 1);
            return new CursorValue(Instant.ofEpochMilli(epochMillis), id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record CursorValue(Instant createdAt, String id) {}
}
