package io.casehub.platform.simulation.generator.test;

import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "capability-spi", capabilities = {"doWork"})
public interface TestCapabilitySpi {
    String id();
    TestCapability doWork();
    boolean supports(Class<?> capability);
}