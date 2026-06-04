package io.casehub.platform.memory.mem0;

import io.casehub.platform.memory.mem0.dto.Mem0AddRequest;
import io.casehub.platform.memory.mem0.dto.Mem0AddResponse;
import io.casehub.platform.memory.mem0.dto.Mem0ListResponse;
import io.casehub.platform.memory.mem0.dto.Mem0SearchRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

// DELETE /memories uses query params (not a body).
// Null @QueryParam values are omitted from the URL by the MicroProfile REST Client automatically.
@RegisterRestClient(configKey = "mem0")
@RegisterProvider(Mem0AuthFilter.class)
@Path("/")
public interface Mem0Client {

    @POST
    @Path("/memories")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Mem0AddResponse add(Mem0AddRequest request);

    @GET
    @Path("/memories")
    @Produces(MediaType.APPLICATION_JSON)
    Mem0ListResponse list(
        @QueryParam("user_id")  String userId,
        @QueryParam("agent_id") String agentId,
        @QueryParam("run_id")   String runId
    );

    @POST
    @Path("/search")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Mem0ListResponse search(Mem0SearchRequest request);

    @DELETE
    @Path("/memories/{memoryId}")
    void deleteById(@PathParam("memoryId") String memoryId);

    @DELETE
    @Path("/memories")
    void deleteAll(
        @QueryParam("user_id")  String userId,
        @QueryParam("agent_id") String agentId,
        @QueryParam("run_id")   String runId
    );
}
