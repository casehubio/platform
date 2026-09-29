package io.casehub.platform.subscription.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

public class SubscriptionExceptionMappers {

    @Provider
    public static class SecurityExceptionMapper implements ExceptionMapper<SecurityException> {
        @Override
        public Response toResponse(SecurityException e) {
            return Response.status(403).build();
        }
    }

    @Provider
    public static class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
        @Override
        public Response toResponse(IllegalArgumentException e) {
            return Response.status(400).entity(e.getMessage()).build();
        }
    }
}
