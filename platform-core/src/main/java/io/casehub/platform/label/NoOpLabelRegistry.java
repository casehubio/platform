package io.casehub.platform.label;

import io.casehub.platform.api.label.LabelDescriptor;
import io.casehub.platform.api.label.LabelRegistry;
import io.casehub.platform.api.path.Path;

import java.util.List;
import java.util.Optional;

public class NoOpLabelRegistry implements LabelRegistry {
    @Override public void register(final String tenancyId, final LabelDescriptor descriptor) {}
    @Override public void deregister(final String tenancyId, final Path path) {}
    @Override public Optional<LabelDescriptor> resolve(final String tenancyId, final Path path) { return Optional.empty(); }
    @Override public List<LabelDescriptor> children(final String tenancyId, final Path parentPath) { return List.of(); }
    @Override public List<LabelDescriptor> subtree(final String tenancyId, final Path rootPath) { return List.of(); }
}
