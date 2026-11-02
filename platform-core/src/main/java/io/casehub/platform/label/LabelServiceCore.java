package io.casehub.platform.label;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.label.LabelApi;
import io.casehub.platform.api.label.LabelDescriptor;
import io.casehub.platform.api.label.LabelRegistry;
import io.casehub.platform.api.path.Path;

import java.util.List;
import java.util.Optional;

public class LabelServiceCore implements LabelApi {

    private final LabelRegistry registry;
    private final CurrentPrincipal principal;

    public LabelServiceCore(LabelRegistry registry, CurrentPrincipal principal) {
        this.registry = registry;
        this.principal = principal;
    }

    @Override
    public List<LabelDescriptor> children(String parentPath) {
        Path path = (parentPath == null || parentPath.isBlank()) ? Path.root() : Path.parse(parentPath);
        return registry.children(principal.tenancyId(), path);
    }

    @Override
    public Optional<LabelDescriptor> resolve(String path) {
        if (path == null || path.isBlank()) return Optional.empty();
        return registry.resolve(principal.tenancyId(), Path.parse(path));
    }

    @Override
    public List<LabelDescriptor> subtree(String rootPath) {
        Path path = (rootPath == null || rootPath.isBlank()) ? Path.root() : Path.parse(rootPath);
        return registry.subtree(principal.tenancyId(), path);
    }
}
