package io.casehub.platform.memory;

import io.casehub.platform.api.memory.EraseRequest;
import io.casehub.platform.api.memory.Memory;
import io.casehub.platform.api.memory.MemoryInput;
import io.casehub.platform.api.memory.MemoryQuery;
import io.smallrye.mutiny.Uni;
import java.util.List;

public interface ReactiveCaseMemoryStore {

    Uni<String> store(MemoryInput input);

    Uni<List<Memory>> query(MemoryQuery query);

    Uni<Void> erase(EraseRequest request);

    /**
     * Erase a specific memory by its assigned memoryId.
     *
     * <p>Default returns a failed Uni matching the blocking interface's
     * {@link io.casehub.platform.api.memory.CaseMemoryStore#eraseById} default contract.
     */
    default Uni<Void> eraseById(String memoryId, String tenantId) {
        return Uni.createFrom().failure(
            new UnsupportedOperationException("eraseById not supported by this adapter"));
    }
}
