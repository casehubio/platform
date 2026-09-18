package io.casehub.platform.simulation.generator.test;

public interface TestUnannotatedSpi {

    String resolve(String key);

    int delete(String key);
}
