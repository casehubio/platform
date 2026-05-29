package io.casehub.platform.api.memory;

import io.casehub.platform.api.identity.CurrentPrincipal;
import java.util.List;

public interface CaseMemoryStore {

    /**
     * Store a memory about an entity. Returns the assigned memoryId.
     *
     * <p>Append-only at the SPI level. The no-op returns {@code ""}.
     * Adapters MUST call {@link MemoryPermissions#assertTenant} before delegating to the backend.
     */
    String store(MemoryInput input);

    /**
     * Recall memories relevant to a query context.
     *
     * <p>Domain isolation is strict equality — only memories tagged with {@code query.domain()}
     * are returned. Non-semantic adapters ignore {@code question} and return
     * entity+domain+tenant+caseId-scoped results ordered by {@code createdAt} descending.
     * Returns an empty list when no adapter is installed.
     */
    List<Memory> query(MemoryQuery query);

    /**
     * Erase memories matching the request. Domain is required — use {@link #eraseEntity}
     * for GDPR Art.17 cross-domain full-entity wipe.
     *
     * <p>Adapters MUST perform hard deletion.
     * Adapters MUST call {@link MemoryPermissions#assertTenant} before delegating to the backend.
     */
    void erase(EraseRequest request);

    /**
     * GDPR Art.17 full-entity wipe across ALL domains for this entity within the tenant.
     *
     * <p>Adapters MUST perform hard deletion across every domain.
     * Adapters MUST call {@link MemoryPermissions#assertTenant} before delegating to the backend.
     *
     * <p>Default throws {@link UnsupportedOperationException} — consistent with
     * {@link #eraseById}. {@code NoOpCaseMemoryStore} overrides with a true no-op.
     * Real adapters must override with actual cross-domain deletion.
     */
    default void eraseEntity(String entityId, String tenantId) {
        throw new UnsupportedOperationException("eraseEntity not supported by this adapter");
    }

    /**
     * Erase a specific memory by its assigned memoryId.
     *
     * <p>The default throws {@link UnsupportedOperationException} — a silent no-op on a
     * GDPR-adjacent erasure would give a false success signal. {@code NoOpCaseMemoryStore}
     * overrides with a true no-op (nothing stored). Real adapters override with actual deletion.
     * Adapters MUST call {@link MemoryPermissions#assertTenant} before delegating to the backend.
     */
    default void eraseById(String memoryId, String tenantId) {
        throw new UnsupportedOperationException("eraseById not supported by this adapter");
    }

    /**
     * Convenience bulk store. Returns assigned memoryIds in input order.
     * Adapters may override for efficiency.
     */
    default List<String> storeAll(List<MemoryInput> inputs) {
        return inputs.stream().map(this::store).toList();
    }

    /**
     * Security guard. Delegates to {@link MemoryPermissions#assertTenant}.
     *
     * @throws SecurityException if tenantId does not match principal.tenancyId()
     */
    default void assertTenant(String tenantId, CurrentPrincipal principal) {
        MemoryPermissions.assertTenant(tenantId, principal);
    }
}
