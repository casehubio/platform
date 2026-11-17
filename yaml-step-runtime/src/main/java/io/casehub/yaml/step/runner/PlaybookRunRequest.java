package io.casehub.yaml.step.runner;

import java.util.Map;

public record PlaybookRunRequest(
        String playbookPath,
        boolean dryRun,
        Map<String, Object> vars,
        String corpusPath,
        String simulationProfile) {

    public PlaybookRunRequest {
        vars = vars != null ? Map.copyOf(vars) : Map.of();
    }
}
