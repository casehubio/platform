package io.casehub.platform.view;

import io.casehub.platform.api.view.SubjectViewSpec;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpSubjectViewStoreTest {

    private final NoOpSubjectViewStore store = new NoOpSubjectViewStore();

    @Test
    void saveReturnsInput() {
        var spec = new SubjectViewSpec(UUID.randomUUID(), "v", "t", "a/**", null, null, null, null);
        assertThat(store.save(spec)).isSameAs(spec);
    }

    @Test
    void findByIdReturnsEmpty() {
        assertThat(store.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByTenancyReturnsEmpty() {
        assertThat(store.findByTenancy("t")).isEmpty();
    }

    @Test
    void deleteDoesNotThrow() {
        store.delete(UUID.randomUUID());
    }
}
