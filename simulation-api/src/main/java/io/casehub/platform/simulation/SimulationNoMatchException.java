package io.casehub.platform.simulation;

public class SimulationNoMatchException extends RuntimeException {

    private final String qualifiedName;
    private final double threshold;

    public SimulationNoMatchException(final String qualifiedName, final double threshold) {
        super("No corpus entry for " + qualifiedName + " scored above threshold " + threshold);
        this.qualifiedName = qualifiedName;
        this.threshold = threshold;
    }

    public String getQualifiedName() { return qualifiedName; }

    public double getThreshold() { return threshold; }
}
