package io.casehub.platform.rest.spring.generator;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.Flow;

@Path("/sse")
@Produces(MediaType.APPLICATION_JSON)
public class SampleSseResource {
    @Inject SampleSseCore core;

    @GET
    @Path("/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Flow.Publisher<String> streamEvents() {
        return core.streamEvents();
    }
}
