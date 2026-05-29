package io.casehub.platform.memory;

import io.casehub.platform.api.memory.CaseMemoryStore;
import io.casehub.platform.api.memory.EraseRequest;
import io.casehub.platform.api.memory.Memory;
import io.casehub.platform.api.memory.MemoryInput;
import io.casehub.platform.api.memory.MemoryQuery;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpCaseMemoryStore implements CaseMemoryStore {

    @Override public String store(MemoryInput input) { return ""; }
    @Override public List<Memory> query(MemoryQuery query) { return List.of(); }
    @Override public void erase(EraseRequest request) {}
    @Override public void eraseById(String memoryId, String tenantId) {}
    @Override public void eraseEntity(String entityId, String tenantId) {}
}
