package io.casehub.platform.subscription.engine;

import io.casehub.platform.api.subscription.SubscribableEvent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTypeObjectTypeTest {

    @Test
    void matches_trueForMatchingEventType() {
        var objectType = new EventTypeObjectType("io.casehub.work.workitem.completed");
        var pojo = new TestEvent("io.casehub.work.workitem.completed", "tenant-1");
        assertThat(objectType.matches(pojo)).isTrue();
    }

    @Test
    void matches_falseForDifferentEventType() {
        var objectType = new EventTypeObjectType("io.casehub.work.workitem.completed");
        var pojo = new TestEvent("io.casehub.work.workitem.created", "tenant-1");
        assertThat(objectType.matches(pojo)).isFalse();
    }

    @Test
    void matches_falseForPojoWithoutTypeMethod() {
        var objectType = new EventTypeObjectType("io.casehub.work.workitem.completed");
        assertThat(objectType.matches("not-a-pojo")).isFalse();
    }

    @Test
    void matches_falseForNull() {
        var objectType = new EventTypeObjectType("io.casehub.work.workitem.completed");
        assertThat(objectType.matches(null)).isFalse();
    }

    @Test
    void getTypeKey_returnsEventTypeString() {
        var objectType = new EventTypeObjectType("io.casehub.work.workitem.completed");
        assertThat(objectType.getTypeKey()).isEqualTo("io.casehub.work.workitem.completed");
    }

    @Test
    void constructor_rejectsNull() {
        assertThatThrownBy(() -> new EventTypeObjectType(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void extractEventType_returnsTypeFromPojoWithTypeMethod() {
        var pojo = new TestEvent("io.casehub.work.workitem.completed", "tenant-1");
        assertThat(EventTypeObjectType.extractEventType(pojo))
                .isEqualTo("io.casehub.work.workitem.completed");
    }

    @Test
    void extractEventType_returnsNullForPojoWithoutTypeMethod() {
        assertThat(EventTypeObjectType.extractEventType("no-type-method")).isNull();
    }

    @Test
    void extractEventType_returnsNullForNull() {
        assertThat(EventTypeObjectType.extractEventType(null)).isNull();
    }

    record TestEvent(String type, String tenancyId) implements SubscribableEvent {}
}
