package io.casehub.platform.agent.http.quarkus;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.NoSuchElementException;

@Provider
public class SessionNotFoundExceptionMapper implements ExceptionMapper<NoSuchElementException> {

    @Override
    public Response toResponse(NoSuchElementException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(exception.getMessage())
                .build();
    }
}
