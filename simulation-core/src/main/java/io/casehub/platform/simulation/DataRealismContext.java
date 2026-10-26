package io.casehub.platform.simulation;

public class DataRealismContext {

    public record Observation(String qualifiedName, DataRealism dataRealism) {}

    private Observation current;

    public void set(String qualifiedName, DataRealism dataRealism) {
        current = new Observation(qualifiedName, dataRealism);
    }

    public Observation current() {
        return current;
    }

    public DataRealism dataRealism() {
        return current != null ? current.dataRealism() : null;
    }
}
