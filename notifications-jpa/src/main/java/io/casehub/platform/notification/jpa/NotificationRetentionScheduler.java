package io.casehub.platform.notification.jpa;

import io.casehub.platform.api.notification.NotificationStatus;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Scheduled purge of old notifications based on retention policies.
 *
 * <p>Purges READ/DISMISSED notifications older than {@code casehub.notification.jpa.retention-days}
 * (default 90 days) and UNREAD notifications older than
 * {@code casehub.notification.jpa.unread-retention-days} (default 365 days).
 *
 * <p>Runs on the schedule defined by {@code casehub.notification.jpa.retention-check-interval}
 * (default 24h).
 *
 * <h2>Test Configuration</h2>
 * Disable scheduler in test resources/application.properties:
 * <pre>
 * quarkus.scheduler.enabled=false
 * </pre>
 */
@ApplicationScoped
public class NotificationRetentionScheduler {

    private static final Logger LOG = Logger.getLogger(NotificationRetentionScheduler.class);

    @Inject
    Mutiny.SessionFactory sf;

    @Inject
    Vertx vertx;

    @ConfigProperty(name = "casehub.notification.jpa.retention-days", defaultValue = "90")
    int retentionDays;

    @ConfigProperty(name = "casehub.notification.jpa.unread-retention-days", defaultValue = "365")
    int unreadRetentionDays;

    @Scheduled(every = "${casehub.notification.jpa.retention-check-interval:24h}")
    void purge() {
        Instant readDismissedCutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        Instant unreadCutoff = Instant.now().minus(unreadRetentionDays, ChronoUnit.DAYS);

        LOG.infof("Starting notification retention purge: READ/DISMISSED < %s, UNREAD < %s",
                readDismissedCutoff, unreadCutoff);

        execute(sf.withTransaction((session, tx) -> {
            Uni<Integer> readDismissedPurge = session.createMutationQuery(
                            "DELETE FROM NotificationEntity " +
                                    "WHERE status IN (:readStatus, :dismissedStatus) AND createdAt < :cutoff")
                    .setParameter("readStatus", NotificationStatus.READ)
                    .setParameter("dismissedStatus", NotificationStatus.DISMISSED)
                    .setParameter("cutoff", readDismissedCutoff)
                    .executeUpdate();

            Uni<Integer> unreadPurge = session.createMutationQuery(
                            "DELETE FROM NotificationEntity " +
                                    "WHERE status = :unreadStatus AND createdAt < :cutoff")
                    .setParameter("unreadStatus", NotificationStatus.UNREAD)
                    .setParameter("cutoff", unreadCutoff)
                    .executeUpdate();

            return Uni.combine().all().unis(readDismissedPurge, unreadPurge)
                    .asTuple()
                    .map(tuple -> tuple.getItem1() + tuple.getItem2());
        }).invoke(totalDeleted ->
                LOG.infof("Notification retention purge completed: %d notifications deleted", totalDeleted)
        ).onFailure().invoke(throwable ->
                LOG.errorf(throwable, "Notification retention purge failed")
        ));
    }

    /**
     * Execute a reactive operation on a Vert.x duplicated context and block for the result.
     * Pattern taken from {@link JpaNotificationStore#execute}.
     */
    private void execute(Uni<?> uni) {
        Context context = VertxContext.getOrCreateDuplicatedContext(vertx);
        VertxContextSafetyToggle.setContextSafe(context, true);
        CompletionStage<?> stage = Uni.createFrom().deferred(() -> uni)
                .runSubscriptionOn(r -> context.runOnContext(v -> r.run()))
                .subscribeAsCompletionStage();
        try {
            stage.toCompletableFuture().join();
        } catch (Exception e) {
            throw new RuntimeException("Retention purge failed", e);
        }
    }
}
