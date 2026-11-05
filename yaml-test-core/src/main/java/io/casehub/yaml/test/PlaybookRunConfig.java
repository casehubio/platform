package io.casehub.yaml.test;

import java.util.Map;

public record PlaybookRunConfig(
        boolean dryRun,
        Map<String, Object> vars,
        String corpusPath,
        String simulationProfile) {

    public PlaybookRunConfig {
        vars = vars != null ? Map.copyOf(vars) : Map.of();
    }

    public static PlaybookRunConfig defaults() {
        return new PlaybookRunConfig(false, Map.of(), null, null);
    }

    public static PlaybookRunConfig forDryRun() {
        return new PlaybookRunConfig(true, Map.of(), null, null);
    }
}
