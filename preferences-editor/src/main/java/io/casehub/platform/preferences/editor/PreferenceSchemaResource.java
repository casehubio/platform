package io.casehub.platform.preferences.editor;

import io.casehub.platform.api.preferences.PreferenceSchemaDescriptor;
import io.casehub.platform.api.preferences.PreferenceSchemaRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;

import java.util.Comparator;
import java.util.List;

@ApplicationScoped
@Path("/preferences/schema")
public class PreferenceSchemaResource {

    @Inject PreferenceSchemaRegistry registry;

    @GET
    public Response schema(@QueryParam("namespace") String namespace,
                           @Context Request request) {
        EntityTag etag = new EntityTag(String.valueOf(registry.version()));
        Response.ResponseBuilder notModified = request.evaluatePreconditions(etag);
        if (notModified != null) {
            return notModified.build();
        }
        List<PreferenceSchemaDescriptor> result = registry.discover().stream()
                .filter(d -> namespace == null || namespace.isBlank() || d.namespace().equals(namespace))
                .sorted(Comparator.comparing(PreferenceSchemaDescriptor::qualifiedName))
                .toList();
        return Response.ok(result).tag(etag).build();
    }
}
