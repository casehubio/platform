package io.casehub.platform.api.memory;

import io.casehub.platform.api.identity.CurrentPrincipal;
import java.util.List;

public interface CaseMemoryStore {

    /**
     * Store a memory about an entity. Returns the assigned memoryId.
     *
     * <p>Append-only at the SPI level. The no-op returns {@code ""}.
     * Adapters MUST call {@link MemoryPermissions#assertTenant} before delegating to the backend.
     *
     * <p><b>Emission pattern:</b> inject {@code CaseMemoryStore} directly and call
     * {@code store()} from your domain event handler. This is the canonical pattern —
     * direct injection keeps exception propagation intact ({@link SecurityException} from
     * {@code assertTenant()} reaches the caller), keeps request context active for
     * {@code @RequestScoped} implementations, and is consistent with the read API
     * ({@link #query(MemoryQuery)}).
     *
     * <p>This analysis assumes an active request scope. Callers in non-request contexts
     * (batch jobs, startup) must activate request scope explicitly before calling any
     * {@code @RequestScoped} SPI implementation.
     *
     * <p><b>{@code @ObservesAsync} is not safe for memory writes.</b> Async observers
     * run on a thread pool where the request scope is not propagated by default — a
     * {@code @RequestScoped CurrentPrincipal} is unavailable, causing
     * {@link jakarta.enterprise.context.ContextNotActiveException} before
     * {@code assertTenant()} fires. Exceptions are also invisible to the original caller,
     * so compliance failures are swallowed silently.
     *
     * <p><b>{@code @Observes} (synchronous) is acceptable</b> — it preserves request
     * context and propagates exceptions normally. A synchronous CDI observer that calls
     * {@code store()} directly is a valid consumption pattern equivalent to Option A
     * with an intervening domain event. The tradeoff is that it makes the store write
     * atomic with the event-firing transaction — desirable for compliance writes that
     * must not persist if the enclosing operation rolls back, but wrong if the caller
     * expects a fire-and-forget side effect.
     *
     * <p><b>Text field guidance:</b> {@link MemoryInput#text()} must be human-readable
     * natural language when using semantic adapters (Mem0, Graphiti) — it is the field
     * embedded for vector search. Use {@link MemoryInput#attributes()} for structured
     * metadata. See {@link MemoryAttributeKeys} for reserved cross-domain attribute keys.
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
     *
     * <p>Adapters that override this method MUST: (1) call
     * {@link MemoryPermissions#assertTenant} for every input; (2) return IDs in input
     * order; (3) ensure no items are durably written if any tenant check fails — via
     * pre-flight for REST-backed adapters, or single-transaction rollback for
     * JDBC-backed adapters. See {@code memory-storeall-transactional-contract.md}
     * for the full contract.
     *
     * <p>The default implementation is not safe for mixed-tenant batches where
     * partial-write prevention is required — override in production adapters.
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
