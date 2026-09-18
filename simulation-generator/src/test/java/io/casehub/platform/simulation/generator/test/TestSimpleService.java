package io.casehub.platform.simulation.generator.test;

import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "test-service")
public interface TestSimpleService {

    String lookup(String id);

    void save(String id, String value);

    int count();
}
