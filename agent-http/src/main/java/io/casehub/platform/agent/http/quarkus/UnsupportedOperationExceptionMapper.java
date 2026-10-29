package io.casehub.platform.agent.http.quarkus;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UnsupportedOperationExceptionMapper implements ExceptionMapper<UnsupportedOperationException> {

    @Override
    public Response toResponse(UnsupportedOperationException exception) {
        return Response.status(501)
                .entity(exception.getMessage())
                .build();
    }
}
