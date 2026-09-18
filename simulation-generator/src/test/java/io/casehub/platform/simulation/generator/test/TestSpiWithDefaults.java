package io.casehub.platform.simulation.generator.test;

import io.casehub.platform.simulation.SimulationEligible;

import java.util.List;

@SimulationEligible(name = "spi-with-defaults")
public interface TestSpiWithDefaults {

    String query(String input);

    void store(String id, String value);

    default List<String> queryAll(List<String> inputs) {
        return inputs.stream().map(this::query).toList();
    }

    default int count() {
        return 0;
    }
}
