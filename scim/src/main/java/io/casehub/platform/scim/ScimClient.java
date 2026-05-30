package io.casehub.platform.scim;

import io.casehub.platform.scim.model.ScimGroupResource;
import io.casehub.platform.scim.model.ScimListResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "scim")
@RegisterProvider(ScimAuthFilter.class)
@Path("/")
public interface ScimClient {

    @GET
    @Path("/Groups")
    ScimListResponse<ScimGroupResource> listGroups(
        @QueryParam("filter") String filter,
        @QueryParam("attributes") String attributes
    );

    @GET
    @Path("/Groups/{id}")
    ScimGroupResource getGroup(
        @PathParam("id") String id,
        @QueryParam("attributes") String attributes
    );
}
