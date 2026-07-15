package io.casehub.platform.api.subscription;

import io.casehub.platform.api.expression.ExpressionEvaluator;
import io.casehub.platform.api.expression.MvelExpressionEvaluator;
import io.casehub.platform.api.notification.NotificationSeverity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates SPI record construction, null validation, defensive copies.
 */
class SubscriptionSpiTest {

    // NotificationTemplate tests

    @Test
    void notificationTemplate_validConstruction() {
        var template = new NotificationTemplate(
                "New {entityType} created",
                "Details: {details}",
                NotificationSeverity.INFO,
                "work-item.created",
                "/items/{entityId}",
                "work-item",
                "id",
                "createdBy"
        );
        assertThat(template.titlePattern()).isEqualTo("New {entityType} created");
        assertThat(template.bodyPattern()).isEqualTo("Details: {details}");
        assertThat(template.severity()).isEqualTo(NotificationSeverity.INFO);
        assertThat(template.category()).isEqualTo("work-item.created");
        assertThat(template.actionUrlPattern()).isEqualTo("/items/{entityId}");
        assertThat(template.entityType()).isEqualTo("work-item");
        assertThat(template.entityIdField()).isEqualTo("id");
        assertThat(template.actorIdField()).isEqualTo("createdBy");
    }

    @Test
    void notificationTemplate_acceptsNullBodyPattern() {
        var template = new NotificationTemplate(
                "Title",
                null,
                NotificationSeverity.INFO,
                "category",
                "/url",
                "entity",
                "id",
                "actor"
        );
        assertThat(template.bodyPattern()).isNull();
    }

    @Test
    void notificationTemplate_acceptsNullActionUrlPattern() {
        var template = new NotificationTemplate(
                "Title",
                "Body",
                NotificationSeverity.INFO,
                "category",
                null,
                "entity",
                "id",
                "actor"
        );
        assertThat(template.actionUrlPattern()).isNull();
    }

    @Test
    void notificationTemplate_rejectsNullTitlePattern() {
        assertThatThrownBy(() -> new NotificationTemplate(
                null,
                "Body",
                NotificationSeverity.INFO,
                "category",
                "/url",
                "entity",
                "id",
                "actor"
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("titlePattern");
    }

    @Test
    void notificationTemplate_rejectsNullSeverity() {
        assertThatThrownBy(() -> new NotificationTemplate(
                "Title",
                "Body",
                null,
                "category",
                "/url",
                "entity",
                "id",
                "actor"
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("severity");
    }

    @Test
    void notificationTemplate_rejectsNullCategory() {
        assertThatThrownBy(() -> new NotificationTemplate(
                "Title",
                "Body",
                NotificationSeverity.INFO,
                null,
                "/url",
                "entity",
                "id",
                "actor"
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("category");
    }

    @Test
    void notificationTemplate_rejectsNullEntityType() {
        assertThatThrownBy(() -> new NotificationTemplate(
                "Title",
                "Body",
                NotificationSeverity.INFO,
                "category",
                "/url",
                null,
                "id",
                "actor"
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("entityType");
    }

    @Test
    void notificationTemplate_rejectsNullEntityIdField() {
        assertThatThrownBy(() -> new NotificationTemplate(
                "Title",
                "Body",
                NotificationSeverity.INFO,
                "category",
                "/url",
                "entity",
                null,
                "actor"
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("entityIdField");
    }

    @Test
    void notificationTemplate_rejectsNullActorIdField() {
        assertThatThrownBy(() -> new NotificationTemplate(
                "Title",
                "Body",
                NotificationSeverity.INFO,
                "category",
                "/url",
                "entity",
                "id",
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("actorIdField");
    }

    // SubscriptionInput tests

    @Test
    void subscriptionInput_validConstruction() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var targets = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        var template = new NotificationTemplate(
                "Title",
                null,
                NotificationSeverity.INFO,
                "category",
                null,
                "entity",
                "id",
                "actor"
        );
        var input = new SubscriptionInput(
                "user-1",
                "tenant-1",
                "My Subscription",
                "work-item.created",
                filters,
                targets,
                false,
                template,
                true,
                null
        );

        assertThat(input.ownerId()).isEqualTo("user-1");
        assertThat(input.tenancyId()).isEqualTo("tenant-1");
        assertThat(input.name()).isEqualTo("My Subscription");
        assertThat(input.eventType()).isEqualTo("work-item.created");
        assertThat(input.filters()).hasSize(1);
        assertThat(input.filters().get(0).type()).isEqualTo("mvel");
        assertThat(input.targets()).hasSize(1);
        assertThat(input.includeActor()).isFalse();
        assertThat(input.template()).isEqualTo(template);
        assertThat(input.enabled()).isTrue();
    }

    @Test
    void subscriptionInput_rejectsNullOwnerId() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var targets = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        var template = createTemplate();
        assertThatThrownBy(() -> new SubscriptionInput(
                null,
                "tenant-1",
                "Name",
                "event-type",
                filters,
                targets,
                false,
                template,
                true,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ownerId");
    }

    @Test
    void subscriptionInput_rejectsNullTenancyId() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var targets = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        var template = createTemplate();
        assertThatThrownBy(() -> new SubscriptionInput(
                "user-1",
                null,
                "Name",
                "event-type",
                filters,
                targets,
                false,
                template,
                true,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenancyId");
    }

    @Test
    void subscriptionInput_rejectsNullName() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var targets = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        var template = createTemplate();
        assertThatThrownBy(() -> new SubscriptionInput(
                "user-1",
                "tenant-1",
                null,
                "event-type",
                filters,
                targets,
                false,
                template,
                true,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    @Test
    void subscriptionInput_rejectsNullEventType() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var targets = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        var template = createTemplate();
        assertThatThrownBy(() -> new SubscriptionInput(
                "user-1",
                "tenant-1",
                "Name",
                null,
                filters,
                targets,
                false,
                template,
                true,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void subscriptionInput_rejectsNullFilters() {
        var targets = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        var template = createTemplate();
        assertThatThrownBy(() -> new SubscriptionInput(
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                null,
                targets,
                false,
                template,
                true,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("filters");
    }

    @Test
    void subscriptionInput_rejectsNullTargets() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var template = createTemplate();
        assertThatThrownBy(() -> new SubscriptionInput(
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                filters,
                null,
                false,
                template,
                true,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("targets");
    }

    @Test
    void subscriptionInput_rejectsNullTemplate() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var targets = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        assertThatThrownBy(() -> new SubscriptionInput(
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                filters,
                targets,
                false,
                null,
                true,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("template");
    }

    @Test
    void subscriptionInput_makesDefensiveCopyOfFilters() {
        var mutableList = new java.util.ArrayList<ExpressionEvaluator>(List.of(new MvelExpressionEvaluator("status == 'active'")));
        var targets = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        var template = createTemplate();
        var input = new SubscriptionInput(
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                mutableList,
                targets,
                false,
                template,
                true,
                null
        );

        // Mutate original list
        mutableList.clear();

        // Input's copy is unaffected
        assertThat(input.filters()).hasSize(1);
    }

    @Test
    void subscriptionInput_makesDefensiveCopyOfTargets() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var mutableTargets = new java.util.ArrayList<>(List.of(new NotificationTarget(TargetType.USER, "user-1")));
        var template = createTemplate();
        var input = new SubscriptionInput(
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                filters,
                mutableTargets,
                false,
                template,
                true,
                null
        );

        // Mutate original list
        mutableTargets.clear();

        // Input's copy is unaffected
        assertThat(input.targets()).hasSize(1);
    }

    // Subscription tests

    @Test
    void subscription_validConstruction() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var targets = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        var template = createTemplate();
        var createdAt = Instant.now();
        var updatedAt = createdAt.plusSeconds(60);

        var subscription = new Subscription(
                "sub-123",
                "user-1",
                "tenant-1",
                "My Subscription",
                "work-item.created",
                filters,
                targets,
                false,
                template,
                true,
                SubscriptionScope.USER,
                createdAt,
                updatedAt
        );

        assertThat(subscription.id()).isEqualTo("sub-123");
        assertThat(subscription.ownerId()).isEqualTo("user-1");
        assertThat(subscription.tenancyId()).isEqualTo("tenant-1");
        assertThat(subscription.name()).isEqualTo("My Subscription");
        assertThat(subscription.eventType()).isEqualTo("work-item.created");
        assertThat(subscription.filters()).hasSize(1);
        assertThat(subscription.targets()).hasSize(1);
        assertThat(subscription.includeActor()).isFalse();
        assertThat(subscription.template()).isEqualTo(template);
        assertThat(subscription.enabled()).isTrue();
        assertThat(subscription.createdAt()).isEqualTo(createdAt);
        assertThat(subscription.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void subscription_rejectsNullId() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var targets = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        var template = createTemplate();
        assertThatThrownBy(() -> new Subscription(
                null,
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                filters,
                targets,
                false,
                template,
                true,
                SubscriptionScope.USER,
                Instant.now(),
                Instant.now()
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
    }

    @Test
    void subscription_rejectsNullCreatedAt() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var targets = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        var template = createTemplate();
        assertThatThrownBy(() -> new Subscription(
                "sub-123",
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                filters,
                targets,
                false,
                template,
                true,
                SubscriptionScope.USER,
                null,
                Instant.now()
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void subscription_rejectsNullUpdatedAt() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var targets = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        var template = createTemplate();
        assertThatThrownBy(() -> new Subscription(
                "sub-123",
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                filters,
                targets,
                false,
                template,
                true,
                SubscriptionScope.USER,
                Instant.now(),
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("updatedAt");
    }

    @Test
    void subscription_makesDefensiveCopyOfFilters() {
        var mutableList = new java.util.ArrayList<ExpressionEvaluator>(List.of(new MvelExpressionEvaluator("status == 'active'")));
        var targets = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        var template = createTemplate();
        var subscription = new Subscription(
                "sub-123",
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                mutableList,
                targets,
                false,
                template,
                true,
                SubscriptionScope.USER,
                Instant.now(),
                Instant.now()
        );

        // Mutate original list
        mutableList.clear();

        // Subscription's copy is unaffected
        assertThat(subscription.filters()).hasSize(1);
    }

    @Test
    void subscription_makesDefensiveCopyOfTargets() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var mutableTargets = new java.util.ArrayList<>(List.of(new NotificationTarget(TargetType.USER, "user-1")));
        var template = createTemplate();
        var subscription = new Subscription(
                "sub-123",
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                filters,
                mutableTargets,
                false,
                template,
                true,
                SubscriptionScope.USER,
                Instant.now(),
                Instant.now()
        );

        // Mutate original list
        mutableTargets.clear();

        // Subscription's copy is unaffected
        assertThat(subscription.targets()).hasSize(1);
    }

    // SubscriptionUpdate tests

    @Test
    void subscriptionUpdate_validConstruction_allFieldsSet() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var targets = List.of(new NotificationTarget(TargetType.USER, "user-2"));
        var template = createTemplate();
        var update = new SubscriptionUpdate(
                "New Name",
                "new-event-type",
                filters,
                targets,
                true,
                template,
                false
        );

        assertThat(update.name()).isEqualTo("New Name");
        assertThat(update.eventType()).isEqualTo("new-event-type");
        assertThat(update.filters()).hasSize(1);
        assertThat(update.targets()).hasSize(1);
        assertThat(update.includeActor()).isTrue();
        assertThat(update.template()).isEqualTo(template);
        assertThat(update.enabled()).isFalse();
    }

    @Test
    void subscriptionUpdate_allFieldsNullable() {
        var update = new SubscriptionUpdate(null, null, null, null, null, null, null);

        assertThat(update.name()).isNull();
        assertThat(update.eventType()).isNull();
        assertThat(update.filters()).isNull();
        assertThat(update.targets()).isNull();
        assertThat(update.includeActor()).isNull();
        assertThat(update.template()).isNull();
        assertThat(update.enabled()).isNull();
    }

    @Test
    void subscriptionUpdate_makesDefensiveCopyOfFilters() {
        var mutableList = new java.util.ArrayList<ExpressionEvaluator>(List.of(new MvelExpressionEvaluator("status == 'active'")));
        var update = new SubscriptionUpdate(
                "Name",
                "event-type",
                mutableList,
                null,
                null,
                createTemplate(),
                true
        );

        // Mutate original list
        mutableList.clear();

        // Update's copy is unaffected
        assertThat(update.filters()).hasSize(1);
    }

    @Test
    void subscriptionUpdate_makesDefensiveCopyOfTargets() {
        var mutableTargets = new java.util.ArrayList<>(List.of(new NotificationTarget(TargetType.USER, "user-1")));
        var update = new SubscriptionUpdate(
                "Name",
                "event-type",
                null,
                mutableTargets,
                null,
                createTemplate(),
                true
        );

        // Mutate original list
        mutableTargets.clear();

        // Update's copy is unaffected
        assertThat(update.targets()).hasSize(1);
    }

    // SubscriptionQuery tests

    @Test
    void subscriptionQuery_validConstruction() {
        var query = new SubscriptionQuery(
                "user-1",
                "tenant-1",
                null,
                true,
                "cursor-abc",
                25
        );

        assertThat(query.ownerId()).isEqualTo("user-1");
        assertThat(query.tenancyId()).isEqualTo("tenant-1");
        assertThat(query.enabled()).isTrue();
        assertThat(query.cursor()).isEqualTo("cursor-abc");
        assertThat(query.limit()).isEqualTo(25);
    }

    @Test
    void subscriptionQuery_acceptsNullEnabledAndCursor() {
        var query = new SubscriptionQuery(
                "user-1",
                "tenant-1",
                null,
                null,
                null,
                10
        );

        assertThat(query.enabled()).isNull();
        assertThat(query.cursor()).isNull();
    }

    @Test
    void subscriptionQuery_rejectsNullOwnerId() {
        assertThatThrownBy(() -> new SubscriptionQuery(
                null,
                "tenant-1",
                null,
                null,
                null,
                10
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ownerId required for USER scope");
    }

    @Test
    void subscriptionQuery_rejectsNullTenancyId() {
        assertThatThrownBy(() -> new SubscriptionQuery(
                "user-1",
                null,
                null,
                null,
                null,
                10
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenancyId");
    }

    @Test
    void subscriptionQuery_rejectsZeroLimit() {
        assertThatThrownBy(() -> new SubscriptionQuery(
                "user-1",
                "tenant-1",
                null,
                null,
                null,
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit must be positive");
    }

    @Test
    void subscriptionQuery_rejectsNegativeLimit() {
        assertThatThrownBy(() -> new SubscriptionQuery(
                "user-1",
                "tenant-1",
                null,
                null,
                null,
                -5
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit must be positive");
    }

    // SubscriptionPage tests

    @Test
    void subscriptionPage_validConstruction() {
        var subscription = new Subscription(
                "sub-123",
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                List.of(),
                List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false,
                createTemplate(),
                true,
                SubscriptionScope.USER,
                Instant.now(),
                Instant.now()
        );
        var page = new SubscriptionPage(List.of(subscription), "cursor-next");

        assertThat(page.subscriptions()).hasSize(1);
        assertThat(page.subscriptions().get(0)).isEqualTo(subscription);
        assertThat(page.nextCursor()).isEqualTo("cursor-next");
    }

    @Test
    void subscriptionPage_acceptsNullCursor() {
        var page = new SubscriptionPage(List.of(), null);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void subscriptionPage_rejectsNullSubscriptions() {
        assertThatThrownBy(() -> new SubscriptionPage(null, "cursor"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("subscriptions");
    }

    @Test
    void subscriptionPage_makesDefensiveCopy() {
        var subscription = new Subscription(
                "sub-123",
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                List.of(),
                List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false,
                createTemplate(),
                true,
                SubscriptionScope.USER,
                Instant.now(),
                Instant.now()
        );
        var mutableList = new java.util.ArrayList<>(List.of(subscription));
        var page = new SubscriptionPage(mutableList, null);

        // Mutate original list
        mutableList.clear();

        // Page's copy is unaffected
        assertThat(page.subscriptions()).hasSize(1);
    }

    @Test
    void subscriptionPage_returnsUnmodifiableList() {
        var page = new SubscriptionPage(List.of(), null);
        assertThatThrownBy(() -> page.subscriptions().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // CDI Event Tests

    @Test
    void subscriptionCreated_validConstruction() {
        var subscription = new Subscription(
                "sub-123",
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                List.of(),
                List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false,
                createTemplate(),
                true,
                SubscriptionScope.USER,
                Instant.now(),
                Instant.now()
        );
        var event = new SubscriptionCreated(subscription);
        assertThat(event.subscription()).isEqualTo(subscription);
    }

    @Test
    void subscriptionCreated_rejectsNullSubscription() {
        assertThatThrownBy(() -> new SubscriptionCreated(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("subscription");
    }

    @Test
    void subscriptionUpdated_validConstruction() {
        var subscription = new Subscription(
                "sub-123",
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                List.of(),
                List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false,
                createTemplate(),
                true,
                SubscriptionScope.USER,
                Instant.now(),
                Instant.now()
        );
        var previous = new Subscription(
                "sub-123",
                "user-1",
                "tenant-1",
                "Old Name",
                "event-type",
                List.of(),
                List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false,
                createTemplate(),
                true,
                SubscriptionScope.USER,
                Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(60)
        );
        var event = new SubscriptionUpdated(subscription, previous);
        assertThat(event.subscription()).isEqualTo(subscription);
        assertThat(event.previous()).isEqualTo(previous);
    }

    @Test
    void subscriptionUpdated_rejectsNullSubscription() {
        var previous = new Subscription(
                "sub-123",
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                List.of(),
                List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false,
                createTemplate(),
                true,
                SubscriptionScope.USER,
                Instant.now(),
                Instant.now()
        );
        assertThatThrownBy(() -> new SubscriptionUpdated(null, previous))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("subscription");
    }

    @Test
    void subscriptionUpdated_rejectsNullPrevious() {
        var subscription = new Subscription(
                "sub-123",
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                List.of(),
                List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false,
                createTemplate(),
                true,
                SubscriptionScope.USER,
                Instant.now(),
                Instant.now()
        );
        assertThatThrownBy(() -> new SubscriptionUpdated(subscription, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("previous");
    }

    @Test
    void subscriptionDeleted_validConstruction() {
        var subscription = new Subscription(
                "sub-123",
                "user-1",
                "tenant-1",
                "Name",
                "event-type",
                List.of(),
                List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false,
                createTemplate(),
                true,
                SubscriptionScope.USER,
                Instant.now(),
                Instant.now()
        );
        var event = new SubscriptionDeleted(subscription);
        assertThat(event.subscription()).isEqualTo(subscription);
    }

    @Test
    void subscriptionDeleted_rejectsNullSubscription() {
        assertThatThrownBy(() -> new SubscriptionDeleted(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("subscription");
    }

    // Helper Methods


    @Test
    void subscription_rejectsNullScope() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var targets     = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        var template    = createTemplate();
        assertThatThrownBy(() -> new Subscription(
                "sub-123", "user-1", "tenant-1", "Name", "event-type",
                filters, targets, false, template, true,
                null, Instant.now(), Instant.now()
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("scope");
    }

    @Test
    void subscriptionInput_nullScopeDefaultsToUser() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var targets     = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        var template    = createTemplate();
        var input = new SubscriptionInput(
                "user-1", "tenant-1", "Name", "event-type",
                filters, targets, false, template, true, null
        );
        assertThat(input.scope()).isEqualTo(SubscriptionScope.USER);
    }

    @Test
    void subscriptionInput_explicitSystemScope() {
        var filters = List.of((ExpressionEvaluator) new MvelExpressionEvaluator("status == 'active'"));
        var targets     = List.of(new NotificationTarget(TargetType.USER, "user-1"));
        var template    = createTemplate();
        var input = new SubscriptionInput(
                "user-1", "tenant-1", "Name", "event-type",
                filters, targets, false, template, true,
                SubscriptionScope.SYSTEM
        );
        assertThat(input.scope()).isEqualTo(SubscriptionScope.SYSTEM);
    }

    @Test
    void subscriptionQuery_systemScopeAllowsNullOwnerId() {
        var query = new SubscriptionQuery(
                null, "tenant-1", SubscriptionScope.SYSTEM, null, null, 10
        );
        assertThat(query.ownerId()).isNull();
        assertThat(query.scope()).isEqualTo(SubscriptionScope.SYSTEM);
    }

    @Test
    void subscriptionQuery_userScopeRejectsNullOwnerId() {
        assertThatThrownBy(() -> new SubscriptionQuery(
                null, "tenant-1", SubscriptionScope.USER, null, null, 10
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ownerId required for USER scope");
    }

    @Test
    void subscriptionQuery_nullScopeRequiresOwnerId() {
        assertThatThrownBy(() -> new SubscriptionQuery(
                null, "tenant-1", null, null, null, 10
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ownerId required for USER scope");
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
