package io.casehub.platform.simulation.restclient.generator.test;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient
@Path("/health")
public interface TestNoConfigKeyClient {

    @GET
    String check();
}
