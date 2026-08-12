package io.casehub.platform.agent;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AgentRuntimeConfig(
        String command,
        List<String> args,
        Map<String, String> env,
        Path workingDirectory
) {
    public AgentRuntimeConfig {
        Objects.requireNonNull(command, "command");
        args = args != null ? List.copyOf(args) : List.of();
        env = env != null ? Map.copyOf(env) : Map.of();
    }
}
