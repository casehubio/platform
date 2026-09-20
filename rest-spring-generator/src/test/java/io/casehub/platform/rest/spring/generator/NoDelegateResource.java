package io.casehub.platform.rest.spring.generator;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/no-delegate")
public class NoDelegateResource {
    @GET
    public String get() { return "static"; }
}
