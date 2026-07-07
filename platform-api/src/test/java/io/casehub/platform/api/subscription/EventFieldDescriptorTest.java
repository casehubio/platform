package io.casehub.platform.api.subscription;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventFieldDescriptorTest {

    @Test
    void rejectsNullName() {
        assertThatThrownBy(() -> new EventFieldDescriptor(null, "Display", "string"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullDisplayName() {
        assertThatThrownBy(() -> new EventFieldDescriptor("name", null, "string"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullType() {
        assertThatThrownBy(() -> new EventFieldDescriptor("name", "Display", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void roundTripsAllFields() {
        var field = new EventFieldDescriptor("assigneeId", "Assignee", "string");
        assertThat(field.name()).isEqualTo("assigneeId");
        assertThat(field.displayName()).isEqualTo("Assignee");
        assertThat(field.type()).isEqualTo("string");
    }
}
