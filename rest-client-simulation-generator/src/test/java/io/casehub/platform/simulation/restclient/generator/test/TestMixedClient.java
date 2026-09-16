package io.casehub.platform.simulation.restclient.generator.test;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import io.casehub.platform.simulation.SimulationEligible;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@SimulationEligible(name = "mixed-spi")
@RegisterRestClient(configKey = "mixed")
@Path("/mixed")
public interface TestMixedClient {

    @GET
    String get();
}
