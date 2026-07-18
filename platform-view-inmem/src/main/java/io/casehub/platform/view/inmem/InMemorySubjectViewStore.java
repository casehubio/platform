package io.casehub.platform.view.inmem;

import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.platform.api.view.SubjectViewStore;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Alternative
@Priority(100)
@ApplicationScoped
public class InMemorySubjectViewStore implements SubjectViewStore {

    private final ConcurrentHashMap<UUID, SubjectViewSpec> store = new ConcurrentHashMap<>();

    @Override
    public SubjectViewSpec save(SubjectViewSpec spec) {
        UUID    id        = spec.id() != null ? spec.id() : UUID.randomUUID();
        Instant createdAt = spec.createdAt() != null ? spec.createdAt() : Instant.now();
        var persisted = new SubjectViewSpec(id, spec.name(), spec.tenancyId(),
                                            spec.labelPattern(), spec.scope(), spec.sortField(),
                                            spec.sortDirection(), spec.additionalConditions(), createdAt);
        store.put(id, persisted);
        return persisted;
    }

    @Override
    public Optional<SubjectViewSpec> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<SubjectViewSpec> findByTenancy(String tenancyId) {
        return store.values().stream()
            .filter(s -> s.tenancyId().equals(tenancyId))
            .toList();
    }

    @Override
    public void delete(UUID id) {
        store.remove(id);
    }
}
