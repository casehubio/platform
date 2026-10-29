package io.casehub.platform.agent.http.quarkus;

import io.casehub.platform.agent.AgentSessionLimitException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AgentSessionLimitExceptionMapper implements ExceptionMapper<AgentSessionLimitException> {

    @Override
    public Response toResponse(AgentSessionLimitException exception) {
        return Response.status(429)
                .entity(exception.getMessage())
                .build();
    }
}
