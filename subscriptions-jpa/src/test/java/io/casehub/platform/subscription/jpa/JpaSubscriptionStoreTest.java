package io.casehub.platform.subscription.jpa;

import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.subscription.Constraint;
import io.casehub.platform.api.subscription.ConstraintOp;
import io.casehub.platform.api.subscription.NotificationTemplate;
import io.casehub.platform.api.subscription.ReactiveSubscriptionStore;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionQuery;
import io.casehub.platform.api.subscription.SubscriptionStore;
import io.casehub.platform.api.subscription.SubscriptionStoreContractTest;
import io.casehub.platform.api.subscription.SubscriptionUpdate;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA subscription store tests — runs both contract tests (blocking SPI via
 * {@link SubscriptionStoreContractTest}) and reactive-specific tests against PostgreSQL
 * DevServices.
 */
@QuarkusTest
public class JpaSubscriptionStoreTest extends SubscriptionStoreContractTest {

    @Inject
    SubscriptionStore blockingStore;

    @Inject
    ReactiveSubscriptionStore reactiveStore;

    @Override
    protected SubscriptionStore store() {
        return blockingStore;
    }

    @Override
    protected void clearState() {
        var context = io.smallrye.common.vertx.VertxContext.getOrCreateDuplicatedContext(
                jakarta.enterprise.inject.spi.CDI.current().select(io.vertx.core.Vertx.class).get());
        io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle.setContextSafe(context, true);
        io.smallrye.mutiny.Uni.createFrom().deferred(() ->
                        Panache.withTransaction(() -> SubscriptionEntity.deleteAll()))
                .runSubscriptionOn(r -> context.runOnContext(v -> r.run()))
                .subscribeAsCompletionStage()
                .toCompletableFuture()
                .join();
    }

    // Reactive SPI Tests — run on Vert.x context with UniAsserter

    @Test
    @RunOnVertxContext
    void reactive_store_persistsSubscription(UniAsserter asserter) {
        var input = createTestInput("user-1", "tenant-1", "Reactive Sub", "work-item.created");
        asserter.assertThat(
                () -> Panache.withTransaction(() -> SubscriptionEntity.deleteAll())
                        .chain(() -> reactiveStore.store(input)),
                subscription -> {
                    assertThat(subscription.id()).isNotNull();
                    assertThat(subscription.name()).isEqualTo("Reactive Sub");
                    assertThat(subscription.eventType()).isEqualTo("work-item.created");
                    assertThat(subscription.enabled()).isTrue();
                });
    }

    @Test
    @RunOnVertxContext
    void reactive_findById_returnsSubscription(UniAsserter asserter) {
        var input = createTestInput("user-1", "tenant-1", "Find Me", "event-type");
        asserter.assertThat(
                () -> Panache.withTransaction(() -> SubscriptionEntity.deleteAll())
                        .chain(() -> reactiveStore.store(input))
                        .chain(sub -> reactiveStore.findById(sub.id(), "user-1", "tenant-1")),
                found -> {
                    assertThat(found).isPresent();
                    assertThat(found.get().name()).isEqualTo("Find Me");
                });
    }

    @Test
    @RunOnVertxContext
    void reactive_findById_wrongUser_returnsEmpty(UniAsserter asserter) {
        var input = createTestInput("user-1", "tenant-1", "Title", "event-type");
        asserter.assertThat(
                () -> Panache.withTransaction(() -> SubscriptionEntity.deleteAll())
                        .chain(() -> reactiveStore.store(input))
                        .chain(sub -> reactiveStore.findById(sub.id(), "user-2", "tenant-1")),
                found -> assertThat(found).isEmpty());
    }

    @Test
    @RunOnVertxContext
    void reactive_find_returnsPaginatedResults(UniAsserter asserter) {
        asserter.assertThat(
                () -> Panache.withTransaction(() -> SubscriptionEntity.deleteAll())
                        .chain(() -> reactiveStore.store(createTestInput("user-1", "tenant-1", "S0", "event-type")))
                        .chain(() -> reactiveStore.store(createTestInput("user-1", "tenant-1", "S1", "event-type")))
                        .chain(() -> reactiveStore.store(createTestInput("user-1", "tenant-1", "S2", "event-type")))
                        .chain(() -> reactiveStore.find(new SubscriptionQuery("user-1", "tenant-1", null, null, 2))),
                page -> {
                    assertThat(page.subscriptions()).hasSize(2);
                    assertThat(page.nextCursor()).isNotNull();
                });
    }

    @Test
    @RunOnVertxContext
    void reactive_update_changesName(UniAsserter asserter) {
        var input = createTestInput("user-1", "tenant-1", "Old Name", "event-type");
        asserter.assertThat(
                () -> Panache.withTransaction(() -> SubscriptionEntity.deleteAll())
                        .chain(() -> reactiveStore.store(input))
                        .chain(sub -> reactiveStore.update(sub.id(), "user-1", "tenant-1",
                                new SubscriptionUpdate("New Name", null, null, null, null))),
                updated -> {
                    assertThat(updated).isPresent();
                    assertThat(updated.get().name()).isEqualTo("New Name");
                    assertThat(updated.get().eventType()).isEqualTo("event-type");
                });
    }

    @Test
    @RunOnVertxContext
    void reactive_update_wrongUser_returnsEmpty(UniAsserter asserter) {
        var input = createTestInput("user-1", "tenant-1", "Title", "event-type");
        asserter.assertThat(
                () -> Panache.withTransaction(() -> SubscriptionEntity.deleteAll())
                        .chain(() -> reactiveStore.store(input))
                        .chain(sub -> reactiveStore.update(sub.id(), "user-2", "tenant-1",
                                new SubscriptionUpdate("New", null, null, null, null))),
                updated -> assertThat(updated).isEmpty());
    }

    @Test
    @RunOnVertxContext
    void reactive_delete_removesSubscription(UniAsserter asserter) {
        var input = createTestInput("user-1", "tenant-1", "Title", "event-type");
        asserter.assertThat(
                () -> Panache.withTransaction(() -> SubscriptionEntity.deleteAll())
                        .chain(() -> reactiveStore.store(input))
                        .chain(sub -> reactiveStore.delete(sub.id(), "user-1", "tenant-1")),
                deleted -> assertThat(deleted).isTrue());
    }

    @Test
    @RunOnVertxContext
    void reactive_delete_wrongUser_returnsFalse(UniAsserter asserter) {
        var input = createTestInput("user-1", "tenant-1", "Title", "event-type");
        asserter.assertThat(
                () -> Panache.withTransaction(() -> SubscriptionEntity.deleteAll())
                        .chain(() -> reactiveStore.store(input))
                        .chain(sub -> reactiveStore.delete(sub.id(), "user-2", "tenant-1")),
                deleted -> assertThat(deleted).isFalse());
    }

    @Test
    @RunOnVertxContext
    void reactive_findAllEnabled_returnsOnlyEnabled(UniAsserter asserter) {
        var enabledInput = createTestInput("user-1", "tenant-1", "Enabled", "event-type");
        var disabledInput = new SubscriptionInput(
                "user-1", "tenant-1", "Disabled", "event-type",
                List.of(), createTemplate(), false);
        asserter.assertThat(
                () -> Panache.withTransaction(() -> SubscriptionEntity.deleteAll())
                        .chain(() -> reactiveStore.store(enabledInput))
                        .chain(() -> reactiveStore.store(disabledInput))
                        .chain(() -> reactiveStore.findAllEnabled().collect().asList()),
                subscriptions -> {
                    assertThat(subscriptions).hasSize(1);
                    assertThat(subscriptions.get(0).name()).isEqualTo("Enabled");
                });
    }

    // Entity mapping verification (blocking store — run on test thread)

    @Test
    void entity_preservesConstraints() {
        var constraints = List.of(
                new Constraint("subject", ConstraintOp.EQ, "case-123"),
                new Constraint("source", ConstraintOp.STARTS_WITH, "/tenants/")
        );
        var input = new SubscriptionInput(
                "user-1", "tenant-1", "With Constraints", "event-type",
                constraints, createTemplate(), true);

        var subscription = blockingStore.store(input);

        assertThat(subscription.constraints()).hasSize(2);
        assertThat(subscription.constraints().get(0).field()).isEqualTo("subject");
        assertThat(subscription.constraints().get(0).op()).isEqualTo(ConstraintOp.EQ);
        assertThat(subscription.constraints().get(0).value()).isEqualTo("case-123");
        assertThat(subscription.constraints().get(1).field()).isEqualTo("source");
        assertThat(subscription.constraints().get(1).op()).isEqualTo(ConstraintOp.STARTS_WITH);
    }

    @Test
    void entity_preservesTemplate() {
        var template = new NotificationTemplate(
                "Work item {entityId} created",
                "Actor {actorId} created work item {entityId}",
                NotificationSeverity.WARNING,
                "work-item.created",
                "/cases/{caseId}/work-items/{entityId}",
                "work-item",
                "entityId",
                "actorId"
        );
        var input = new SubscriptionInput(
                "user-1", "tenant-1", "Template Sub", "event-type",
                List.of(), template, true);

        var subscription = blockingStore.store(input);

        assertThat(subscription.template().titlePattern()).isEqualTo("Work item {entityId} created");
        assertThat(subscription.template().bodyPattern()).isEqualTo("Actor {actorId} created work item {entityId}");
        assertThat(subscription.template().severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(subscription.template().category()).isEqualTo("work-item.created");
        assertThat(subscription.template().actionUrlPattern()).isEqualTo("/cases/{caseId}/work-items/{entityId}");
        assertThat(subscription.template().entityType()).isEqualTo("work-item");
        assertThat(subscription.template().entityIdField()).isEqualTo("entityId");
        assertThat(subscription.template().actorIdField()).isEqualTo("actorId");
    }

    @Test
    void entity_handlesEmptyConstraints() {
        var input = createTestInput("user-1", "tenant-1", "No Constraints", "event-type");

        var subscription = blockingStore.store(input);

        assertThat(subscription.constraints()).isEmpty();
    }

    @Test
    void entity_roundTripsConstraintsOnUpdate() {
        var subscription = blockingStore.store(
                createTestInput("user-1", "tenant-1", "Name", "event-type"));

        var newConstraints = List.of(new Constraint("newField", ConstraintOp.NEQ, "excluded"));
        var update = new SubscriptionUpdate(null, null, newConstraints, null, null);
        var updated = blockingStore.update(subscription.id(), "user-1", "tenant-1", update);

        assertThat(updated).isPresent();
        assertThat(updated.get().constraints()).hasSize(1);
        assertThat(updated.get().constraints().get(0).field()).isEqualTo("newField");
        assertThat(updated.get().constraints().get(0).op()).isEqualTo(ConstraintOp.NEQ);
    }

    // Cursor pagination verification (blocking store — run on test thread)

    @Test
    void cursor_paginationCoversAllResults() {
        for (int i = 0; i < 5; i++) {
            blockingStore.store(createTestInput("user-1", "tenant-1", "S" + i, "event-type"));
        }

        var allIds = new java.util.ArrayList<String>();
        String cursor = null;
        int pages = 0;
        do {
            var query = new SubscriptionQuery("user-1", "tenant-1", null, cursor, 2);
            var page = blockingStore.find(query);
            for (Subscription s : page.subscriptions()) {
                allIds.add(s.id());
            }
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null);

        assertThat(allIds).hasSize(5);
        assertThat(pages).isEqualTo(3);
        assertThat(allIds).doesNotHaveDuplicates();
    }

    // Helper

    private SubscriptionInput createTestInput(String userId, String tenancyId, String name, String eventType) {
        return new SubscriptionInput(userId, tenancyId, name, eventType,
                List.of(), createTemplate(), true);
    }

    private NotificationTemplate createTemplate() {
        return new NotificationTemplate(
                "Title",
                null,
                NotificationSeverity.INFO,
                "category",
                null,
                "entity",
                "id",
                "actor"
        );
    }
}
