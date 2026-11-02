package io.casehub.platform.label.memory;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.label.LabelDescriptor;
import io.casehub.platform.api.label.LabelDeregistered;
import io.casehub.platform.api.label.LabelRegistered;
import io.casehub.platform.api.label.LabelRegistry;
import io.casehub.platform.api.path.Path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class InMemoryLabelRegistry implements LabelRegistry {

    private static final Comparator<LabelDescriptor> CHILD_ORDER =
            Comparator.comparingInt(LabelDescriptor::sortOrder)
                    .thenComparing(LabelDescriptor::displayName);

    private static final Comparator<LabelDescriptor> SUBTREE_ORDER =
            Comparator.comparing(d -> d.path().value());

    private final ConcurrentHashMap<LabelKey, LabelDescriptor> store = new ConcurrentHashMap<>();
    private final Consumer<LabelRegistered> onRegistered;
    private final Consumer<LabelDeregistered> onDeregistered;

    public InMemoryLabelRegistry(Consumer<LabelRegistered> onRegistered,
                                 Consumer<LabelDeregistered> onDeregistered) {
        this.onRegistered = onRegistered;
        this.onDeregistered = onDeregistered;
    }

    public InMemoryLabelRegistry() {
        this(null, null);
    }

    @Override
    public void register(final String tenancyId, final LabelDescriptor descriptor) {
        Objects.requireNonNull(tenancyId, "tenancyId must not be null");
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        if (!tenancyId.equals(descriptor.tenancyId())) {
            throw new IllegalArgumentException(
                    "tenancyId parameter '" + tenancyId + "' does not match descriptor tenancyId '" + descriptor.tenancyId() + "'");
        }

        ensureAncestors(tenancyId, descriptor.path());
        store.put(new LabelKey(tenancyId, descriptor.path().value()), descriptor);

        if (onRegistered != null) {
            onRegistered.accept(new LabelRegistered(descriptor));
        }
    }

    @Override
    public void deregister(final String tenancyId, final Path path) {
        Objects.requireNonNull(tenancyId, "tenancyId must not be null");
        Objects.requireNonNull(path, "path must not be null");

        var removed = store.remove(new LabelKey(tenancyId, path.value()));
        if (removed != null && onDeregistered != null) {
            onDeregistered.accept(new LabelDeregistered(tenancyId, path));
        }
    }

    @Override
    public Optional<LabelDescriptor> resolve(final String tenancyId, final Path path) {
        Objects.requireNonNull(tenancyId, "tenancyId must not be null");
        Objects.requireNonNull(path, "path must not be null");

        var tenant = store.get(new LabelKey(tenancyId, path.value()));
        if (tenant != null) return Optional.of(tenant);

        if (!TenancyConstants.PLATFORM_TENANT_ID.equals(tenancyId)) {
            var global = store.get(new LabelKey(TenancyConstants.PLATFORM_TENANT_ID, path.value()));
            return Optional.ofNullable(global);
        }
        return Optional.empty();
    }

    @Override
    public List<LabelDescriptor> children(final String tenancyId, final Path parentPath) {
        Objects.requireNonNull(tenancyId, "tenancyId must not be null");
        Objects.requireNonNull(parentPath, "parentPath must not be null");

        int childDepth = parentPath.depth() + 1;
        Map<String, LabelDescriptor> merged = new LinkedHashMap<>();

        if (!TenancyConstants.PLATFORM_TENANT_ID.equals(tenancyId)) {
            for (var entry : store.entrySet()) {
                var d = entry.getValue();
                if (d.tenancyId().equals(TenancyConstants.PLATFORM_TENANT_ID)
                        && isChildOf(d.path(), parentPath, childDepth)) {
                    merged.put(d.path().value(), d);
                }
            }
        }

        for (var entry : store.entrySet()) {
            var d = entry.getValue();
            if (d.tenancyId().equals(tenancyId) && isChildOf(d.path(), parentPath, childDepth)) {
                merged.put(d.path().value(), d);
            }
        }

        var result = new ArrayList<>(merged.values());
        result.sort(CHILD_ORDER);
        return List.copyOf(result);
    }

    @Override
    public List<LabelDescriptor> subtree(final String tenancyId, final Path rootPath) {
        Objects.requireNonNull(tenancyId, "tenancyId must not be null");
        Objects.requireNonNull(rootPath, "rootPath must not be null");

        Map<String, LabelDescriptor> merged = new LinkedHashMap<>();

        if (!TenancyConstants.PLATFORM_TENANT_ID.equals(tenancyId)) {
            for (var entry : store.entrySet()) {
                var d = entry.getValue();
                if (d.tenancyId().equals(TenancyConstants.PLATFORM_TENANT_ID)
                        && isDescendantOf(d.path(), rootPath)) {
                    merged.put(d.path().value(), d);
                }
            }
        }

        for (var entry : store.entrySet()) {
            var d = entry.getValue();
            if (d.tenancyId().equals(tenancyId) && isDescendantOf(d.path(), rootPath)) {
                merged.put(d.path().value(), d);
            }
        }

        var result = new ArrayList<>(merged.values());
        result.sort(SUBTREE_ORDER);
        return List.copyOf(result);
    }

    private void ensureAncestors(String tenancyId, Path path) {
        Path ancestor = path.parent();
        while (ancestor != null) {
            var key = new LabelKey(tenancyId, ancestor.value());
            store.putIfAbsent(key, new LabelDescriptor(tenancyId, ancestor, null, 0));
            ancestor = ancestor.parent();
        }
    }

    private static boolean isChildOf(Path candidate, Path parent, int expectedDepth) {
        if (candidate.depth() != expectedDepth) return false;
        if (parent.depth() == 0) return true;
        return parent.isAncestorOf(candidate);
    }

    private static boolean isDescendantOf(Path candidate, Path root) {
        if (root.depth() == 0) return candidate.depth() > 0;
        return root.isAncestorOf(candidate);
    }
}
