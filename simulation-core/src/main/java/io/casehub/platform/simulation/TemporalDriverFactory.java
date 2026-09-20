package io.casehub.platform.simulation;

@FunctionalInterface
public interface TemporalDriverFactory<E> {
    TemporalSimulationDriver<E> create();
}
