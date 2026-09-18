package io.casehub.platform.simulation;

public interface SimulationStrategy<I, O> {

    O resolve(I input);

    boolean canResolve(I input);
}
