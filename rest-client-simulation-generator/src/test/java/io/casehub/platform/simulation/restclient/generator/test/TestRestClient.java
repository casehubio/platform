package io.casehub.platform.simulation.restclient.generator.test;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "test-api")
@Path("/api")
public interface TestRestClient {

    @GET
    @Path("/items/{id}")
    String getItem(@PathParam("id") String id);

    @POST
    @Path("/items")
    String createItem(String body);

    @GET
    @Path("/items")
    String listItems(@QueryParam("page") int page, @QueryParam("size") int size);

    @GET
    @Path("/items/{id}/details")
    String getItemDetails(@PathParam("id") String id, @QueryParam("expand") String expand);

    default String healthCheck() {
        return "ok";
    }
}
