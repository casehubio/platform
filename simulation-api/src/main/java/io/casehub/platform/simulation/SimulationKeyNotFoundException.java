package io.casehub.platform.simulation;

public class SimulationKeyNotFoundException extends RuntimeException {

    private final String key;

    public SimulationKeyNotFoundException(final String key) {
        super("No simulation data found for key: " + key);
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
