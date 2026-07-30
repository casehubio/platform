package io.casehub.platform.api.preferences;

import java.util.Optional;
import java.util.Set;

public interface PreferenceSchemaRegistry {

    void register(PreferenceSchemaDescriptor descriptor);

    Optional<PreferenceSchemaDescriptor> resolve(String qualifiedName);

    Set<PreferenceSchemaDescriptor> discover();

    default long version() { return 0L; }
}
