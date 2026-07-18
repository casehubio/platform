package io.casehub.platform.api.view;

import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectViewSpecTest {

    @Test
    void minimalSpec() {
        var spec = new SubjectViewSpec(
            UUID.randomUUID(), "test-view", "tenant-1",
            "iot/**", Path.root(), null, null, null);
        assertThat(spec.name()).isEqualTo("test-view");
        assertThat(spec.labelPattern()).isEqualTo("iot/**");
        assertThat(spec.sortField()).isNull();
        assertThat(spec.createdAt()).isNull();
    }

    @Test
    void nullNameThrows() {
        assertThatThrownBy(() -> new SubjectViewSpec(
            UUID.randomUUID(), null, "tenant-1",
            "iot/**", null, null, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullTenancyIdThrows() {
        assertThatThrownBy(() -> new SubjectViewSpec(
            UUID.randomUUID(), "name", null,
            "iot/**", null, null, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullLabelPatternThrows() {
        assertThatThrownBy(() -> new SubjectViewSpec(
            UUID.randomUUID(), "name", "tenant-1",
            null, null, null, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sortFieldNullable() {
        var spec = new SubjectViewSpec(
            UUID.randomUUID(), "name", "tenant-1",
            "iot/**", null, "createdAt", null, null);
        assertThat(spec.sortField()).isEqualTo("createdAt");
        assertThat(spec.sortDirection()).isNull();
    }
}
