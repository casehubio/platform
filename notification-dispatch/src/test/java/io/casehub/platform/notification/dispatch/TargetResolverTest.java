package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.subscription.NotificationTarget;
import io.casehub.platform.api.subscription.NotificationTemplate;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.TargetType;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TargetResolverTest {

    private static final String TENANT = "tenant-1";
    private static final Instant NOW = Instant.now();

    private static final NotificationTemplate TEMPLATE = new NotificationTemplate(
            "Title", null, NotificationSeverity.INFO, "test.category",
            null, "work-item", "entityId", "actorId");

    // Stub GroupMembershipProvider that returns configurable members
    private final GroupMembershipProvider groupProvider = groupName -> {
        if ("engineers".equals(groupName)) {
            return Set.of(
                    new GroupMember("user-1", "Alice"),
                    new GroupMember("user-2", "Bob"),
                    new GroupMember("user-3", "Charlie"));
        }
        if ("empty-group".equals(groupName)) {
            return Set.of();
        }
        return Set.of();
    };

    private final TargetResolver resolver = new TargetResolver(groupProvider);

    @Test
    void resolve_userTarget_addsDirectly() {
        var sub = subscription(
                List.of(new NotificationTarget(TargetType.USER, "user-42")),
                false);
        var pojo = new TestEvent("evt-1", "actor-99");

        var result = resolver.resolve(sub, pojo);

        assertThat(result).containsExactly("user-42");
    }

    @Test
    void resolve_groupTarget_expandsViaGroupMembershipProvider() {
        var sub = subscription(
                List.of(new NotificationTarget(TargetType.GROUP, "engineers")),
                false);
        var pojo = new TestEvent("evt-1", "actor-external");

        var result = resolver.resolve(sub, pojo);

        assertThat(result).containsExactlyInAnyOrder("user-1", "user-2", "user-3");
    }

    @Test
    void resolve_groupTarget_emptyGroup_returnsEmpty() {
        var sub = subscription(
                List.of(new NotificationTarget(TargetType.GROUP, "empty-group")),
                false);
        var pojo = new TestEvent("evt-1", "actor-external");

        var result = resolver.resolve(sub, pojo);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_eventFieldTarget_extractsFromPojo() {
        var sub = subscription(
                List.of(new NotificationTarget(TargetType.EVENT_FIELD, "assigneeId")),
                false);
        var pojo = new TestEventWithAssignee("evt-1", "actor-99", "user-assignee");

        var result = resolver.resolve(sub, pojo);

        assertThat(result).containsExactly("user-assignee");
    }

    @Test
    void resolve_eventFieldTarget_nullField_skips() {
        var sub = subscription(
                List.of(new NotificationTarget(TargetType.EVENT_FIELD, "missingField")),
                false);
        var pojo = new TestEvent("evt-1", "actor-99");

        var result = resolver.resolve(sub, pojo);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_deduplicatesAcrossTargets() {
        var sub = subscription(
                List.of(
                        new NotificationTarget(TargetType.USER, "user-1"),
                        new NotificationTarget(TargetType.GROUP, "engineers")),
                false);
        var pojo = new TestEvent("evt-1", "actor-external");

        var result = resolver.resolve(sub, pojo);

        // user-1 appears in both USER target and GROUP, should be deduplicated
        assertThat(result).containsExactlyInAnyOrder("user-1", "user-2", "user-3");
    }

    @Test
    void resolve_excludesActor_byDefault() {
        var sub = subscription(
                List.of(new NotificationTarget(TargetType.USER, "actor-user")),
                false);
        var pojo = new TestEvent("evt-1", "actor-user");

        var result = resolver.resolve(sub, pojo);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_includesActor_whenIncludeActorTrue() {
        var sub = subscription(
                List.of(new NotificationTarget(TargetType.USER, "actor-user")),
                true);
        var pojo = new TestEvent("evt-1", "actor-user");

        var result = resolver.resolve(sub, pojo);

        assertThat(result).containsExactly("actor-user");
    }

    @Test
    void resolve_emptyAfterExclusion_returnsEmpty() {
        // Only target is the actor, and includeActor is false
        var sub = subscription(
                List.of(new NotificationTarget(TargetType.USER, "the-actor")),
                false);
        var pojo = new TestEvent("evt-1", "the-actor");

        var result = resolver.resolve(sub, pojo);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_multipleTargetTypes_combined() {
        var sub = subscription(
                List.of(
                        new NotificationTarget(TargetType.USER, "direct-user"),
                        new NotificationTarget(TargetType.GROUP, "engineers"),
                        new NotificationTarget(TargetType.EVENT_FIELD, "assigneeId")),
                false);
        var pojo = new TestEventWithAssignee("evt-1", "actor-external", "user-assignee");

        var result = resolver.resolve(sub, pojo);

        assertThat(result).containsExactlyInAnyOrder(
                "direct-user", "user-1", "user-2", "user-3", "user-assignee");
    }

    // --- helpers ---

    private Subscription subscription(List<NotificationTarget> targets, boolean includeActor) {
        return new Subscription(
                "sub-1", "owner-1", TENANT, "Test Sub", "test.event",
                List.of(), targets, includeActor, TEMPLATE, true, NOW, NOW);
    }

    record TestEvent(String entityId, String actorId) {}
    record TestEventWithAssignee(String entityId, String actorId, String assigneeId) {}
}
