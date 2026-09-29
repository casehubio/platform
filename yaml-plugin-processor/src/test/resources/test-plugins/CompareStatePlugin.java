package test.plugins;

import io.casehub.yaml.plugin.api.*;
import java.util.Map;

@Plugin(value = "compare-state", description = "Compares actual state against desired conditions")
public record CompareStatePlugin(@Optional String absentWhen, @Optional String driftedWhen, @Optional String presentWhen) {
    @Execute
    public Result run() {
        if (absentWhen != null && Boolean.parseBoolean(absentWhen)) {
            return Result.of(Map.of("nodeStatus", "ABSENT"));
        }
        if (driftedWhen != null && Boolean.parseBoolean(driftedWhen)) {
            return Result.of(Map.of("nodeStatus", "DRIFTED"));
        }
        if (presentWhen != null && Boolean.parseBoolean(presentWhen)) {
            return Result.of(Map.of("nodeStatus", "PRESENT"));
        }
        return Result.of(Map.of("nodeStatus", "UNKNOWN"));
    }
}
