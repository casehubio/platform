package io.casehub.platform.rest.spring.generator;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

@Path("/webhook")
public class MultivaluedHeaderResource {

    @Inject
    MultivaluedHeaderCore core;

    @Context
    HttpHeaders httpHeaders;

    @POST
    @Path("/multi/{id}")
    public Response processMultivalued(@PathParam("id") String id, String body) {
        return Response.ok().build();
    }

    @POST
    @Path("/flat/{id}")
    public Response processFlat(@PathParam("id") String id, String body) {
        return Response.ok().build();
    }
}
