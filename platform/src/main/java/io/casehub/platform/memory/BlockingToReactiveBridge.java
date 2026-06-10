package io.casehub.platform.memory;

import io.casehub.platform.api.memory.CaseMemoryStore;
import io.casehub.platform.api.memory.EraseRequest;
import io.casehub.platform.api.memory.Memory;
import io.casehub.platform.api.memory.MemoryInput;
import io.casehub.platform.api.memory.MemoryQuery;
import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@DefaultBean
@ApplicationScoped
public class BlockingToReactiveBridge implements ReactiveCaseMemoryStore {

    @Inject CaseMemoryStore delegate;

    @Override
    public Uni<String> store(MemoryInput input) {
        return Uni.createFrom().item(() -> delegate.store(input))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<List<Memory>> query(MemoryQuery query) {
        return Uni.createFrom().item(() -> delegate.query(query))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<List<String>> storeAll(List<MemoryInput> inputs) {
        return Uni.createFrom().item(() -> delegate.storeAll(inputs))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Void> erase(EraseRequest request) {
        return Uni.createFrom().<Void>item(() -> { delegate.erase(request); return null; })
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Void> eraseById(String memoryId, String tenantId) {
        return Uni.createFrom().<Void>item(() -> { delegate.eraseById(memoryId, tenantId); return null; })
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Integer> eraseEntity(String entityId, String tenantId) {
        return Uni.createFrom().item(() -> delegate.eraseEntity(entityId, tenantId))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
