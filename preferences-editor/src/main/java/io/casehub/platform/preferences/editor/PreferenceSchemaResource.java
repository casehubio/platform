package io.casehub.platform.preferences.editor;

import io.casehub.platform.api.preferences.PreferenceSchemaDescriptor;
import io.casehub.platform.api.preferences.PreferenceSchemaRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import java.util.Comparator;
import java.util.List;

@ApplicationScoped
@Path("/preferences/schema")
public class PreferenceSchemaResource {

    @Inject PreferenceSchemaRegistry registry;

    @GET
    public List<PreferenceSchemaDescriptor> schema(@QueryParam("namespace") String namespace) {
        return registry.discover().stream()
                .filter(d -> namespace == null || namespace.isBlank() || d.namespace().equals(namespace))
                .sorted(Comparator.comparing(PreferenceSchemaDescriptor::qualifiedName))
                .toList();
    }
}
