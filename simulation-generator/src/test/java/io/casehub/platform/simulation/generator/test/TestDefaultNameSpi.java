package io.casehub.platform.simulation.generator.test;

import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible
public interface TestDefaultNameSpi {

    String process(String input);
}
