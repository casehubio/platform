package io.casehub.platform.rest.spring.generator;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Optional;

@Path("/items")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SampleResource {

    @Inject
    SampleCore core;

    @GET
    public List<String> listItems(@QueryParam("tenancyId") String tenancyId) {
        return core.listItems(tenancyId);
    }

    @GET
    @Path("/{id}")
    public Optional<String> getItem(@PathParam("id") String id) {
        return core.getItem(id);
    }

    @POST
    public String createItem(String body) {
        return core.createItem(body);
    }

    @DELETE
    @Path("/{id}")
    public void deleteItem(@PathParam("id") String id) {
        core.deleteItem(id);
    }
}
