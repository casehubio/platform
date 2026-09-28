package io.casehub.platform.rest.spring.generator;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

@Path("/dispatch")
public class StatusBearingResource {
    @Inject
    StatusBearingCore core;

    @POST
    @Path("/{id}")
    public Response dispatch(@PathParam("id") String id, String body) {
        var result = core.dispatch(id, body);
        return Response.status(result.status()).entity(result.body()).build();
    }
}
