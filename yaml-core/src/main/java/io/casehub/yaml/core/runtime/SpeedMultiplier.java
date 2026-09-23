package io.casehub.yaml.core.runtime;

@FunctionalInterface
public interface SpeedMultiplier {
    double currentSpeed();

    static SpeedMultiplier identity() {return () -> 1.0;}

}
