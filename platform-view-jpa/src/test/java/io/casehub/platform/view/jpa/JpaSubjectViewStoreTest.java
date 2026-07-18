package io.casehub.platform.view.jpa;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.view.SubjectViewSpec;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestTransaction
class JpaSubjectViewStoreTest {

    @Inject
    JpaSubjectViewStore store;

    @Test
    void saveAndFind() {
        var spec = new SubjectViewSpec(null, "iot-triage", "t1",
                                       "iot/triage/**", Path.root(), "createdAt", "DESC", null, null);
        var saved = store.save(spec);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.createdAt()).isNotNull();

        var found = store.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("iot-triage");
        assertThat(found.get().labelPattern()).isEqualTo("iot/triage/**");}

    @Test
    void findByTenancy() {
        store.save(new SubjectViewSpec(null, "v1", "t1", "a/**",
                                       null, null, null, null, null));
        store.save(new SubjectViewSpec(null, "v2", "t1", "b/**",
                                       null, null, null, null, null));
        store.save(new SubjectViewSpec(null, "v3", "t2", "c/**",
                                       null, null, null, null, null));

        assertThat(store.findByTenancy("t1")).hasSize(2);
        assertThat(store.findByTenancy("t2")).hasSize(1);
        assertThat(store.findByTenancy("t3")).isEmpty();}

    @Test
    void deleteRemoves() {
        var saved = store.save(new SubjectViewSpec(null, "v", "t1",
                                                   "a/**", null, null, null, null, null));
        store.delete(saved.id());
        assertThat(store.findById(saved.id())).isEmpty();}

    @Test
    void deleteNonExistentDoesNotThrow() {
        store.delete(UUID.randomUUID());
    }

    @Test
    void saveUpdatesExisting() {
        var saved = store.save(new SubjectViewSpec(null, "v1", "t1",
                                                   "a/**", null, null, null, null, null));
        store.save(new SubjectViewSpec(saved.id(), "v1-updated", "t1",
                                       "b/**", null, null, null, null, saved.createdAt()));

        var found = store.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().labelPattern()).isEqualTo("b/**");}
}
