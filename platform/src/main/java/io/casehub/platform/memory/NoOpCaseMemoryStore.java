package io.casehub.platform.memory;

import io.casehub.platform.api.memory.CaseMemoryStore;
import io.casehub.platform.api.memory.EraseRequest;
import io.casehub.platform.api.memory.GraphCaseMemoryStore;
import io.casehub.platform.api.memory.GraphMemoryQuery;
import io.casehub.platform.api.memory.Memory;
import io.casehub.platform.api.memory.MemoryCapability;
import io.casehub.platform.api.memory.MemoryInput;
import io.casehub.platform.api.memory.MemoryQuery;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * No-op {@link CaseMemoryStore} and {@link GraphCaseMemoryStore} — satisfies both injection
 * types when no real adapter is deployed. Erasure methods are true no-ops:
 * nothing stored → erasure is trivially satisfied. This adapter is the only one where
 * {@link #capabilities()} returns {@link Set#of()} yet erase methods do not throw.
 */
@DefaultBean
@ApplicationScoped
public class NoOpCaseMemoryStore implements GraphCaseMemoryStore {

    @Override public String store(final MemoryInput input) { return ""; }
    @Override public List<Memory> query(final MemoryQuery query) { return List.of(); }
    @Override public void erase(final EraseRequest request) {}
    @Override public void eraseById(final String memoryId, final String entityId, final String tenantId) {}
    @Override public int eraseEntity(final String entityId, final String tenantId) { return 0; }
    @Override public List<Memory> graphQuery(final GraphMemoryQuery query) { return List.of(); }

    @Override
    public Set<MemoryCapability> capabilities() {
        return Set.of();
    }

    @Override
    public List<String> storeAll(final List<MemoryInput> inputs) {
        return Collections.nCopies(inputs.size(), "");
    }
}
