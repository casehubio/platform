package io.casehub.platform.subscription.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.subscription.ReactiveSubscriptionStore;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionCreated;
import io.casehub.platform.api.subscription.SubscriptionDeleted;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionPage;
import io.casehub.platform.api.subscription.SubscriptionQuery;
import io.casehub.platform.api.subscription.SubscriptionScope;
import io.casehub.platform.api.subscription.SubscriptionUpdate;
import io.casehub.platform.api.subscription.SubscriptionUpdated;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed reactive subscription store using Hibernate Reactive Panache.
 * Native {@link ReactiveSubscriptionStore} implementation — not a bridge.
 *
 * <p>Cursor pagination uses keyset on {@code (created_at DESC, id DESC)}.
 * Cursor encodes {@code createdAt|id} as Base64.
 *
 * <p>Constraints and template are stored as JSON TEXT columns, serialized and
 * deserialized via Jackson {@link ObjectMapper}.
 */
@ApplicationScoped
public class JpaReactiveSubscriptionStore implements ReactiveSubscriptionStore {

    @Inject
    ObjectMapper mapper;

    @Inject
    Event<SubscriptionCreated> createdEvent;

    @Inject
    Event<SubscriptionUpdated> updatedEvent;

    @Inject
    Event<SubscriptionDeleted> deletedEvent;

    // Cursor encoding: "createdAt_epochMillis|id" → Base64
    private static String encodeCursor(Instant createdAt, String id) {
        String raw = createdAt.toEpochMilli() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());
    }

    private static CursorValue decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor));
            int    sep = raw.indexOf('|');
            if (sep == -1) {return null;}
            long   epochMillis = Long.parseLong(raw.substring(0, sep));
            String id          = raw.substring(sep + 1);
            return new CursorValue(Instant.ofEpochMilli(epochMillis), id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public Uni<Subscription> store(SubscriptionInput input) {
        return Panache.withTransaction(() -> {
            SubscriptionEntity entity = SubscriptionEntity.fromInput(input, mapper);
            return entity.persist()
                         .replaceWith(entity);
        }).map(entity -> {
            Subscription subscription = entity.toSubscription(mapper);
            createdEvent.fireAsync(new SubscriptionCreated(subscription));
            return subscription;
        });
    }

    @Override
    public Uni<Optional<Subscription>> findById(String id, String ownerId, String tenancyId) {
        return Panache.withSession(() ->
                                           SubscriptionEntity.<SubscriptionEntity>find(
                                                                     "id = ?1 AND tenancyId = ?2 AND (ownerId = ?3 OR scope = 'SYSTEM')",
                                                                     id, tenancyId, ownerId)
                                                             .firstResult()
                                                             .map(entity -> entity == null
                                                                            ? Optional.empty()
                                                                            : Optional.of(entity.toSubscription(mapper))));
    }

    @Override
    public Uni<SubscriptionPage> find(SubscriptionQuery query) {
        return Panache.withSession(() -> {
            var effectiveScope = query.scope() != null ? query.scope() : SubscriptionScope.USER;

            StringBuilder hql    = new StringBuilder("FROM SubscriptionEntity WHERE tenancyId = ?1");
            List<Object>  params = new ArrayList<>();
            params.add(query.tenancyId());
            int paramIndex = 2;

            if (effectiveScope == SubscriptionScope.SYSTEM) {
                hql.append(" AND scope = 'SYSTEM'");
            } else {
                hql.append(" AND ownerId = ?").append(paramIndex).append(" AND scope = 'USER'");
                params.add(query.ownerId());
                paramIndex++;
            }

            if (query.enabled() != null) {
                hql.append(" AND enabled = ?").append(paramIndex);
                params.add(query.enabled());
                paramIndex++;
            }

            if (query.cursor() != null) {
                CursorValue cursor = decodeCursor(query.cursor());
                if (cursor != null) {
                    hql.append(" AND (createdAt < ?").append(paramIndex);
                    params.add(cursor.createdAt);
                    paramIndex++;
                    hql.append(" OR (createdAt = ?").append(paramIndex);
                    params.add(cursor.createdAt);
                    paramIndex++;
                    hql.append(" AND id < ?").append(paramIndex).append("))");
                    params.add(cursor.id);
                    paramIndex++;
                }
            }

            hql.append(" ORDER BY createdAt DESC, id DESC");

            int fetchLimit = query.limit() + 1;

            return SubscriptionEntity.<SubscriptionEntity>find(hql.toString(), params.toArray())
                                     .range(0, fetchLimit - 1)
                                     .list()
                                     .map(entities -> {
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
                                     });
        });
    }

    @Override
    public Uni<Optional<Subscription>> update(String id, String ownerId, String tenancyId,
                                              SubscriptionUpdate update) {
        return Panache.withTransaction(() ->
                                               SubscriptionEntity.<SubscriptionEntity>find(
                                                                         "id = ?1 AND tenancyId = ?2 AND (ownerId = ?3 OR scope = 'SYSTEM')",
                                                                         id, tenancyId, ownerId)
                                                                 .firstResult()
                                                                 .map(entity -> {
                                                                     if (entity == null) {
                                                                         return Optional.<UpdateResult>empty();
                                                                     }
                                                                     Subscription previous = entity.toSubscription(mapper);
                                                                     applyUpdate(entity, update);
                                                                     Subscription updated = entity.toSubscription(mapper);
                                                                     return Optional.of(new UpdateResult(updated, previous));
                                                                 }))
                      .map(opt -> {
                          if (opt.isPresent()) {
                              UpdateResult result = opt.get();
                              updatedEvent.fireAsync(new SubscriptionUpdated(result.updated, result.previous));
                              return Optional.of(result.updated);
                          }
                          return Optional.empty();
                      });
    }

    // Private Methods

    @Override
    public Uni<Boolean> delete(String id, String ownerId, String tenancyId) {
        return Panache.withTransaction(() ->
                                               SubscriptionEntity.<SubscriptionEntity>find(
                                                                         "id = ?1 AND tenancyId = ?2 AND (ownerId = ?3 OR scope = 'SYSTEM')",
                                                                         id, tenancyId, ownerId)
                                                                 .firstResult()
                                                                 .chain(entity -> {
                                                                     if (entity == null) {
                                                                         return Uni.createFrom().item(Optional.<Subscription>empty());
                                                                     }
                                                                     Subscription subscription = entity.toSubscription(mapper);
                                                                     return entity.delete()
                                                                                  .replaceWith(Optional.of(subscription));
                                                                 }))
                      .map(opt -> {
                          if (opt.isPresent()) {
                              deletedEvent.fireAsync(new SubscriptionDeleted(opt.get()));
                              return true;
                          }
                          return false;
                      });
    }

    @Override
    public Multi<Subscription> findAllEnabled() {
        return Panache.withSession(() ->
                                           SubscriptionEntity.<SubscriptionEntity>find("enabled = true")
                                                             .list()
                                                             .map(entities -> {
                                                                 List<Subscription> subscriptions = new ArrayList<>(entities.size());
                                                                 for (SubscriptionEntity entity : entities) {
                                                                     subscriptions.add(entity.toSubscription(mapper));
                                                                 }
                                                                 return subscriptions;
                                                             }))
                      .onItem().transformToMulti(Multi.createFrom()::iterable);
    }

    private void applyUpdate(SubscriptionEntity entity, SubscriptionUpdate update) {
        if (update.name() != null) {
            entity.name = update.name();
        }
        if (update.eventType() != null) {
            entity.eventType = update.eventType();
        }
        if (update.constraints() != null) {
            entity.constraintsJson = SubscriptionEntity.serializeConstraints(update.constraints(), mapper);
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

    private record CursorValue(Instant createdAt, String id) {}

    private record UpdateResult(Subscription updated, Subscription previous) {}
}
