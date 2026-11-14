package io.casehub.platform.simulation.generator.test;

import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "extensible-spi")
public interface TestExtensibleSpi extends TestExtensibleBase {
    String resolve(String id);
}
