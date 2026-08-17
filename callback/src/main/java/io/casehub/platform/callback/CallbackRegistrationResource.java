package io.casehub.platform.callback;

import io.casehub.platform.api.callback.CallbackRegistration;
import io.casehub.platform.api.callback.CallbackRegistrationRequest;
import io.casehub.platform.api.callback.CallbackRegistry;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/casehub/callbacks")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("**")
public class CallbackRegistrationResource {

    @Inject
    CallbackRegistry callbackRegistry;

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public CallbackRegistration register(final CallbackRegistrationRequest request) {
        return callbackRegistry.register(request);
    }

    @PUT
    @Path("/{id}/heartbeat")
    public Response heartbeat(@PathParam("id") final String id) {
        if (callbackRegistry.findById(id).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        callbackRegistry.heartbeat(id);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    public Response deregister(@PathParam("id") final String id) {
        callbackRegistry.deregister(id);
        return Response.noContent().build();
    }
}
