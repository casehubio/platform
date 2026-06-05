package io.casehub.platform.memory.inmem;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.memory.*;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Alternative
@Priority(10)
@ApplicationScoped
public class InMemoryMemoryStore implements CaseMemoryStore {

    private final ConcurrentHashMap<BucketKey, CopyOnWriteArrayList<Memory>> store
        = new ConcurrentHashMap<>();
    private final CurrentPrincipal principal;

    @Inject
    public InMemoryMemoryStore(CurrentPrincipal principal) {
        this.principal = principal;
    }

    @Override
    public String store(MemoryInput input) {
        MemoryPermissions.assertTenant(input.tenantId(), principal);
        String memoryId = UUID.randomUUID().toString();
        Memory memory = new Memory(
            memoryId, input.entityId(), input.domain(), input.tenantId(),
            input.caseId(), input.text(), input.attributes(), Instant.now()
        );
        store.computeIfAbsent(
            new BucketKey(input.tenantId(), input.entityId(), input.domain()),
            k -> new CopyOnWriteArrayList<>()
        ).add(memory);
        return memoryId;
    }

    @Override
    public List<Memory> query(MemoryQuery query) {
        MemoryPermissions.assertTenant(query.tenantId(), principal);
        // MemoryOrder is ignored — in-mem always sorts chronologically (createdAt DESC).
        return query.entityIds().stream()
            .flatMap(entityId -> store.getOrDefault(
                    new BucketKey(query.tenantId(), entityId, query.domain()),
                    new CopyOnWriteArrayList<>()
                ).stream()
            )
            .filter(m -> query.caseId() == null || query.caseId().equals(m.caseId()))
            .filter(m -> query.since() == null || !m.createdAt().isBefore(query.since()))
            .filter(m -> query.question() == null
                || m.text().toLowerCase().contains(query.question().toLowerCase()))
            .sorted(Comparator.comparing(Memory::createdAt).reversed())
            .limit(query.limit())
            .toList();
    }

    @Override
    public void erase(EraseRequest request) {
        MemoryPermissions.assertTenant(request.tenantId(), principal);
        var key = new BucketKey(request.tenantId(), request.entityId(), request.domain());
        store.computeIfPresent(key, (k, memories) ->
            new CopyOnWriteArrayList<>(memories.stream()
                .filter(m -> request.caseId() != null && !request.caseId().equals(m.caseId()))
                .toList())
        );
    }

    @Override
    public void eraseById(String memoryId, String tenantId) {
        MemoryPermissions.assertTenant(tenantId, principal);
        store.entrySet().stream()
            .filter(e -> e.getKey().tenantId().equals(tenantId))
            .forEach(e -> e.getValue().removeIf(m -> m.memoryId().equals(memoryId)));
    }

    @Override
    public void eraseEntity(String entityId, String tenantId) {
        MemoryPermissions.assertTenant(tenantId, principal);
        store.keySet().removeIf(
            k -> k.tenantId().equals(tenantId) && k.entityId().equals(entityId)
        );
    }
}
