package io.casehub.platform.oidc;

import io.casehub.platform.api.identity.MissingTenancyException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class MissingTenancyExceptionMapper implements ExceptionMapper<MissingTenancyException> {

    @Override
    public Response toResponse(MissingTenancyException exception) {
        String body = "{\"error\":\"missing_tenancy\","
            + "\"message\":\"No tenancy identifier found — checked JWT claims and SecurityIdentity attributes\","
            + "\"actorId\":\"" + exception.actorId().replace("\"", "\\\"") + "\"}";
        return Response.status(Response.Status.FORBIDDEN)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(body)
            .build();
    }
}
