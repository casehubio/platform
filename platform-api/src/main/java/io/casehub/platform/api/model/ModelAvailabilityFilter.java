package io.casehub.platform.api.model;

@FunctionalInterface
public interface ModelAvailabilityFilter {
    boolean isAvailable(ModelDescriptor descriptor);

    ModelAvailabilityFilter ALWAYS_AVAILABLE = descriptor -> true;
}
