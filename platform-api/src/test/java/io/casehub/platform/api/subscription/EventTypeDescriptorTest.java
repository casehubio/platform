package io.casehub.platform.api.subscription;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTypeDescriptorTest {

    @Test
    void rejectsNullEventType() {
        assertThatThrownBy(() -> new EventTypeDescriptor(null, "Name", "desc", List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullDisplayName() {
        assertThatThrownBy(() -> new EventTypeDescriptor("type", null, "desc", List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullFields() {
        assertThatThrownBy(() -> new EventTypeDescriptor("type", "Name", "desc", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void acceptsNullDescription() {
        var descriptor = new EventTypeDescriptor("type", "Name", null, List.of());
        assertThat(descriptor.description()).isNull();
    }

    @Test
    void fieldsAreDefensivelyCopied() {
        var field = new EventFieldDescriptor("id", "ID", "string");
        var mutable = new java.util.ArrayList<>(List.of(field));
        var descriptor = new EventTypeDescriptor("type", "Name", "desc", mutable);
        mutable.add(new EventFieldDescriptor("extra", "Extra", "string"));
        assertThat(descriptor.fields()).hasSize(1);
    }

    @Test
    void fieldsListIsUnmodifiable() {
        var field = new EventFieldDescriptor("id", "ID", "string");
        var descriptor = new EventTypeDescriptor("type", "Name", "desc", List.of(field));
        assertThatThrownBy(() -> descriptor.fields().add(new EventFieldDescriptor("x", "X", "string")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void roundTripsAllFields() {
        var field = new EventFieldDescriptor("assigneeId", "Assignee", "string");
        var descriptor = new EventTypeDescriptor(
                "io.casehub.work.workitem.completed",
                "Work Item Completed",
                "Fired when a work item completes",
                List.of(field));
        assertThat(descriptor.eventType()).isEqualTo("io.casehub.work.workitem.completed");
        assertThat(descriptor.displayName()).isEqualTo("Work Item Completed");
        assertThat(descriptor.description()).isEqualTo("Fired when a work item completes");
        assertThat(descriptor.fields()).containsExactly(field);
    }
}
