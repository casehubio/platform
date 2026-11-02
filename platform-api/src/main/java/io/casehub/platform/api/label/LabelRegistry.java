package io.casehub.platform.api.label;

import io.casehub.platform.api.path.Path;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped registry of label tree nodes.
 *
 * <p>Provides registration, resolution, enumeration, and deregistration of
 * {@link LabelDescriptor} instances keyed by {@code (tenancyId, path)}.
 *
 * <h2>Tenant isolation</h2>
 * <p>All read operations merge the caller's tenant labels with platform-global
 * labels registered under {@link io.casehub.platform.api.identity.TenancyConstants#PLATFORM_TENANT_ID}.
 * Tenant-specific labels take precedence on conflict (same path registered at both scopes).
 *
 * <h2>Tree structure</h2>
 * <p>The tree is implicit in paths. Registering {@code IT/Hardware/Laptops} implies
 * that {@code IT} and {@code IT/Hardware} exist as ancestors. Implementations
 * auto-create ancestor nodes with display names derived from path segments.
 * Explicitly registering an ancestor overrides these defaults. Auto-created
 * ancestors do not fire {@link LabelRegistered} events — only explicitly
 * registered nodes do.
 */
public interface LabelRegistry {

    /**
     * Register or update a label node. {@code (tenancyId, path)} is the unique key —
     * upsert semantics.
     */
    void register(String tenancyId, LabelDescriptor descriptor);

    /**
     * Deregister a label node. Exact match on {@code (tenancyId, path)}.
     * No-op if not found. Does not remove descendant nodes.
     */
    void deregister(String tenancyId, Path path);

    /**
     * Resolve a single label node by path for the given tenant.
     *
     * <ol>
     *   <li>Returns the tenant-specific descriptor if one exists.</li>
     *   <li>Otherwise returns the platform-global descriptor if one exists.</li>
     *   <li>Otherwise returns empty.</li>
     * </ol>
     */
    Optional<LabelDescriptor> resolve(String tenancyId, Path path);

    /**
     * Returns the immediate children of the given parent path, merging
     * platform-global and tenant-specific children. Tenant wins on conflict.
     * Results are ordered by {@link LabelDescriptor#sortOrder()} then
     * {@link LabelDescriptor#displayName()}.
     *
     * <p>Pass {@link Path#root()} to get top-level label nodes.
     */
    List<LabelDescriptor> children(String tenancyId, Path parentPath);

    /**
     * Returns all descendants of the given root path, merging
     * platform-global and tenant-specific nodes. Tenant wins on conflict.
     * Results are ordered depth-first by path.
     *
     * <p>Pass {@link Path#root()} to get the entire label tree.
     */
    List<LabelDescriptor> subtree(String tenancyId, Path rootPath);
}
