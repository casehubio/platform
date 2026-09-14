package io.casehub.platform.preferences.editor.quarkus;

import io.casehub.platform.api.preferences.PreferenceSchemaDescriptor;
import io.casehub.platform.api.preferences.PreferenceSchemaRegistry;
import io.casehub.platform.preferences.editor.PreferenceSchemaApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class PreferenceSchemaService implements PreferenceSchemaApi {

    private final PreferenceSchemaRegistry registry;

    @Inject
    public PreferenceSchemaService(PreferenceSchemaRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<PreferenceSchemaDescriptor> schema(String namespace) {
        return registry.discover().stream()
                .filter(d -> namespace == null || namespace.isBlank() || d.namespace().equals(namespace))
                .sorted(Comparator.comparing(PreferenceSchemaDescriptor::qualifiedName))
                .toList();
    }
}
