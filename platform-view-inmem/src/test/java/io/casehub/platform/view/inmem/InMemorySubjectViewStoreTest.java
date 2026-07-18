package io.casehub.platform.view.inmem;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.view.SubjectViewSpec;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySubjectViewStoreTest {

    private final InMemorySubjectViewStore store = new InMemorySubjectViewStore();

    private SubjectViewSpec spec(String name, String pattern) {
        return new SubjectViewSpec(null, name, "t1", pattern,
                                   Path.root(), null, null, null, null);
    }

    @Test
    void saveAssignsId() {
        var saved = store.save(spec("v", "iot/**"));
        assertThat(saved.id()).isNotNull();
    }

    @Test
    void savePreservesExistingId() {
        var id = UUID.randomUUID();
        var input = new SubjectViewSpec(id, "v", "t1", "iot/**",
                                        null, null, null, null, null);
        assertThat(store.save(input).id()).isEqualTo(id);}

    @Test
    void findByIdAfterSave() {
        var saved = store.save(spec("v", "iot/**"));
        assertThat(store.findById(saved.id())).isPresent()
            .get().extracting("name").isEqualTo("v");
    }

    @Test
    void findByIdNotFound() {
        assertThat(store.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByTenancy() {
        store.save(spec("v1", "a/**"));
        store.save(spec("v2", "b/**"));
        store.save(new SubjectViewSpec(null, "v3", "other-tenant",
                                       "c/**", null, null, null, null, null));

        assertThat(store.findByTenancy("t1")).hasSize(2);
        assertThat(store.findByTenancy("other-tenant")).hasSize(1);}

    @Test
    void delete() {
        var saved = store.save(spec("v", "iot/**"));
        store.delete(saved.id());
        assertThat(store.findById(saved.id())).isEmpty();
    }

    @Test
    void deleteNonExistentDoesNotThrow() {
        store.delete(UUID.randomUUID());
    }

    @Test
    void saveUpdatesExisting() {
        var saved = store.save(spec("v1", "a/**"));
        store.save(new SubjectViewSpec(saved.id(), "v1-updated",
                                       "t1", "b/**", null, null, null, null, null));
        assertThat(store.findById(saved.id())).isPresent()
                                              .get().extracting("labelPattern").isEqualTo("b/**");}

    @Test
    void saveAssignsCreatedAt() {
        var saved = store.save(spec("v", "iot/**"));
        assertThat(saved.createdAt()).isNotNull();
    }

    @Test
    void savePreservesAdditionalConditions() {
        var input = new SubjectViewSpec(null, "v", "t1", "iot/**",
                                        null, null, null, "status == 'OPEN'", null);
        var saved = store.save(input);
        assertThat(saved.additionalConditions()).isEqualTo("status == 'OPEN'");
    }
}
