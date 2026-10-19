package io.casehub.platform.agent.react;

import java.time.Duration;
import java.util.Objects;

public record ToolCallRecord(
        String name,
        String arguments,
        String output,
        boolean error,
        Duration duration) {
    public ToolCallRecord {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(duration, "duration");
    }
}
