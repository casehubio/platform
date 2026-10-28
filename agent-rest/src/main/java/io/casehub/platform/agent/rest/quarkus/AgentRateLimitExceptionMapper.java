package io.casehub.platform.agent.rest.quarkus;

import io.casehub.platform.agent.AgentRateLimitException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AgentRateLimitExceptionMapper implements ExceptionMapper<AgentRateLimitException> {

    @Override
    public Response toResponse(AgentRateLimitException exception) {
        return Response.status(429)
                .header("Retry-After", String.valueOf(
                        (long) Math.ceil(exception.retryAfterMillis() / 1000.0)))
                .entity(exception.getMessage())
                .build();
    }
}
