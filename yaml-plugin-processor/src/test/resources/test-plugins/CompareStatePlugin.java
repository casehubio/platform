package test.plugins;

import io.casehub.yaml.plugin.api.*;
import java.util.Map;

@StepPlugin(value = "compare-state", description = "Compares actual state against desired conditions")
public record CompareStatePlugin(@Optional String absentWhen, @Optional String driftedWhen, @Optional String presentWhen) {
    @Execute
    public StepResult run() {
        if (absentWhen != null && Boolean.parseBoolean(absentWhen)) {
            return StepResult.of(Map.of("nodeStatus", "ABSENT"));
        }
        if (driftedWhen != null && Boolean.parseBoolean(driftedWhen)) {
            return StepResult.of(Map.of("nodeStatus", "DRIFTED"));
        }
        if (presentWhen != null && Boolean.parseBoolean(presentWhen)) {
            return StepResult.of(Map.of("nodeStatus", "PRESENT"));
        }
        return StepResult.of(Map.of("nodeStatus", "UNKNOWN"));
    }
}
