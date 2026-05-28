package io.casehub.platform.memory;

import io.casehub.platform.api.memory.CaseMemoryStore;
import io.casehub.platform.api.memory.EraseRequest;
import io.casehub.platform.api.memory.Memory;
import io.casehub.platform.api.memory.MemoryInput;
import io.casehub.platform.api.memory.MemoryQuery;
import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@DefaultBean
@ApplicationScoped
public class BlockingToReactiveBridge implements ReactiveCaseMemoryStore {

    @Inject
    CaseMemoryStore delegate;

    @Override
    public Uni<String> store(MemoryInput input) {
        return Uni.createFrom().item(() -> delegate.store(input));
    }

    @Override
    public Uni<List<Memory>> query(MemoryQuery query) {
        return Uni.createFrom().item(() -> delegate.query(query));
    }

    @Override
    public Uni<Void> erase(EraseRequest request) {
        return Uni.createFrom().voidItem().invoke(() -> delegate.erase(request));
    }

    @Override
    public Uni<Void> eraseById(String memoryId, String tenantId) {
        return Uni.createFrom().voidItem()
            .invoke(() -> delegate.eraseById(memoryId, tenantId));
    }
}
