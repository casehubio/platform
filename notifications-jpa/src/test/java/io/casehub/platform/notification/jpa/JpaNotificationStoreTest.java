package io.casehub.platform.notification.jpa;

import io.casehub.platform.api.notification.Notification;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationQuery;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationSource;
import io.casehub.platform.api.notification.NotificationStatus;
import io.casehub.platform.api.notification.NotificationStore;
import io.casehub.platform.api.notification.NotificationStoreContractTest;
import io.casehub.platform.api.notification.ReactiveNotificationStore;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA notification store tests — runs both contract tests (blocking SPI via
 * {@link NotificationStoreContractTest}) and reactive-specific tests against PostgreSQL
 * DevServices.
 */
@QuarkusTest
public class JpaNotificationStoreTest extends NotificationStoreContractTest {

    @Inject
    NotificationStore blockingStore;

    @Inject
    ReactiveNotificationStore reactiveStore;

    @Override
    protected NotificationStore store() {
        return blockingStore;
    }

    @Override
    protected void clearState() {
        // Delete all notifications between tests via blocking store's execute path
        var context = io.smallrye.common.vertx.VertxContext.getOrCreateDuplicatedContext(
                jakarta.enterprise.inject.spi.CDI.current().select(io.vertx.core.Vertx.class).get());
        io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle.setContextSafe(context, true);
        io.smallrye.mutiny.Uni.createFrom().deferred(() ->
                        Panache.withTransaction(() -> NotificationEntity.deleteAll()))
                .runSubscriptionOn(r -> context.runOnContext(v -> r.run()))
                .subscribeAsCompletionStage()
                .toCompletableFuture()
                .join();
    }

    // Reactive SPI Tests — run on Vert.x context with UniAsserter

    @Test
    @RunOnVertxContext
    void reactive_store_persistsNotification(UniAsserter asserter) {
        var input = createTestInput("user-1", "tenant-1", "Reactive Title", "category");
        asserter.assertThat(
                () -> Panache.withTransaction(() -> NotificationEntity.deleteAll())
                        .chain(() -> reactiveStore.store(input)),
                notification -> {
                    assertThat(notification.id()).isNotNull();
                    assertThat(notification.title()).isEqualTo("Reactive Title");
                    assertThat(notification.status()).isEqualTo(NotificationStatus.UNREAD);
                });
    }

    @Test
    @RunOnVertxContext
    void reactive_storeAll_persistsMultiple(UniAsserter asserter) {
        var input1 = createTestInput("user-1", "tenant-1", "R1", "category");
        var input2 = createTestInput("user-1", "tenant-1", "R2", "category");
        asserter.assertThat(
                () -> Panache.withTransaction(() -> NotificationEntity.deleteAll())
                        .chain(() -> reactiveStore.storeAll(List.of(input1, input2))),
                results -> {
                    assertThat(results).hasSize(2);
                    assertThat(results.get(0).title()).isEqualTo("R1");
                    assertThat(results.get(1).title()).isEqualTo("R2");
                });
    }

    @Test
    @RunOnVertxContext
    void reactive_find_returnsPaginatedResults(UniAsserter asserter) {
        asserter.assertThat(
                () -> Panache.withTransaction(() -> NotificationEntity.deleteAll())
                        .chain(() -> reactiveStore.store(createTestInput("user-1", "tenant-1", "N0", "category")))
                        .chain(() -> reactiveStore.store(createTestInput("user-1", "tenant-1", "N1", "category")))
                        .chain(() -> reactiveStore.store(createTestInput("user-1", "tenant-1", "N2", "category")))
                        .chain(() -> reactiveStore.find(new NotificationQuery("user-1", "tenant-1", null, null, null, 2))),
                page -> {
                    assertThat(page.notifications()).hasSize(2);
                    assertThat(page.nextCursor()).isNotNull();
                });
    }

    @Test
    @RunOnVertxContext
    void reactive_unreadCount_returnsCorrectCount(UniAsserter asserter) {
        asserter.assertThat(
                () -> Panache.withTransaction(() -> NotificationEntity.deleteAll())
                        .chain(() -> reactiveStore.store(createTestInput("user-1", "tenant-1", "N1", "category")))
                        .chain(() -> reactiveStore.store(createTestInput("user-1", "tenant-1", "N2", "category")))
                        .chain(() -> reactiveStore.unreadCount("user-1", "tenant-1")),
                count -> assertThat(count).isEqualTo(2));
    }

    @Test
    @RunOnVertxContext
    void reactive_markRead_transitionsToRead(UniAsserter asserter) {
        asserter.assertThat(
                () -> Panache.withTransaction(() -> NotificationEntity.deleteAll())
                        .chain(() -> reactiveStore.store(createTestInput("user-1", "tenant-1", "Title", "category")))
                        .chain(notif -> reactiveStore.markRead(notif.id(), "user-1", "tenant-1")),
                updated -> {
                    assertThat(updated).isPresent();
                    assertThat(updated.get().status()).isEqualTo(NotificationStatus.READ);
                    assertThat(updated.get().readAt()).isNotNull();
                });
    }

    @Test
    @RunOnVertxContext
    void reactive_dismiss_transitionsToDismissed(UniAsserter asserter) {
        asserter.assertThat(
                () -> Panache.withTransaction(() -> NotificationEntity.deleteAll())
                        .chain(() -> reactiveStore.store(createTestInput("user-1", "tenant-1", "Title", "category")))
                        .chain(notif -> reactiveStore.dismiss(notif.id(), "user-1", "tenant-1")),
                updated -> {
                    assertThat(updated).isPresent();
                    assertThat(updated.get().status()).isEqualTo(NotificationStatus.DISMISSED);
                    assertThat(updated.get().dismissedAt()).isNotNull();
                });
    }

    @Test
    @RunOnVertxContext
    void reactive_markAllRead_returnsCount(UniAsserter asserter) {
        asserter.assertThat(
                () -> Panache.withTransaction(() -> NotificationEntity.deleteAll())
                        .chain(() -> reactiveStore.store(createTestInput("user-1", "tenant-1", "N1", "category")))
                        .chain(() -> reactiveStore.store(createTestInput("user-1", "tenant-1", "N2", "category")))
                        .chain(() -> reactiveStore.markAllRead("user-1", "tenant-1")),
                count -> assertThat(count).isEqualTo(2));
    }

    @Test
    @RunOnVertxContext
    void reactive_markRead_wrongUser_returnsEmpty(UniAsserter asserter) {
        asserter.assertThat(
                () -> Panache.withTransaction(() -> NotificationEntity.deleteAll())
                        .chain(() -> reactiveStore.store(createTestInput("user-1", "tenant-1", "Title", "category")))
                        .chain(notif -> reactiveStore.markRead(notif.id(), "user-2", "tenant-1")),
                updated -> assertThat(updated).isEmpty());
    }

    @Test
    @RunOnVertxContext
    void reactive_dismiss_wrongTenant_returnsEmpty(UniAsserter asserter) {
        asserter.assertThat(
                () -> Panache.withTransaction(() -> NotificationEntity.deleteAll())
                        .chain(() -> reactiveStore.store(createTestInput("user-1", "tenant-1", "Title", "category")))
                        .chain(notif -> reactiveStore.dismiss(notif.id(), "user-1", "tenant-2")),
                updated -> assertThat(updated).isEmpty());
    }

    // Entity mapping verification (blocking store — run on test thread)

    @Test
    void entity_preservesAllSourceFields() {
        var source = new NotificationSource("evt-99", "case", "case-42", "actor-7");
        var input = new NotificationInput("user-1", "tenant-1", "Title", "Body",
                "case.updated", NotificationSeverity.WARNING, "/cases/42", source);

        var notification = blockingStore.store(input);

        assertThat(notification.source().eventId()).isEqualTo("evt-99");
        assertThat(notification.source().entityType()).isEqualTo("case");
        assertThat(notification.source().entityId()).isEqualTo("case-42");
        assertThat(notification.source().actorId()).isEqualTo("actor-7");
        assertThat(notification.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(notification.actionUrl()).isEqualTo("/cases/42");
    }

    @Test
    void entity_handlesNullableFields() {
        var source = new NotificationSource("evt-1", "type", "id-1", "actor-1");
        var input = new NotificationInput("user-1", "tenant-1", "Title", null,
                "category", NotificationSeverity.INFO, null, source);

        var notification = blockingStore.store(input);

        assertThat(notification.body()).isNull();
        assertThat(notification.actionUrl()).isNull();
    }

    // Cursor pagination verification (blocking store — run on test thread)

    @Test
    void cursor_paginationCoversAllResults() {
        for (int i = 0; i < 5; i++) {
            blockingStore.store(createTestInput("user-1", "tenant-1", "N" + i, "category"));
        }

        var allIds = new java.util.ArrayList<String>();
        String cursor = null;
        int pages = 0;
        do {
            var query = new NotificationQuery("user-1", "tenant-1", null, null, cursor, 2);
            var page = blockingStore.find(query);
            for (Notification n : page.notifications()) {
                allIds.add(n.id());
            }
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null);

        assertThat(allIds).hasSize(5);
        assertThat(pages).isEqualTo(3);
        assertThat(allIds).doesNotHaveDuplicates();
    }

    // Helper

    private NotificationInput createTestInput(String userId, String tenancyId, String title, String category) {
        var source = new NotificationSource("evt-" + System.nanoTime(), "work-item", "wi-456", "actor-789");
        return new NotificationInput(userId, tenancyId, title, "Body for " + title,
                category, NotificationSeverity.INFO, "/action", source);
    }
}
