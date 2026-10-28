package io.casehub.platform.agent.rest.quarkus;

import io.casehub.platform.agent.AgentTimeoutException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AgentTimeoutExceptionMapper implements ExceptionMapper<AgentTimeoutException> {

    @Override
    public Response toResponse(AgentTimeoutException exception) {
        return Response.status(Response.Status.GATEWAY_TIMEOUT)
                .entity(exception.getMessage())
                .build();
    }
}
