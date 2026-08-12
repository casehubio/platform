package io.casehub.platform.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AgentRuntimeConfigTest {

    @Test
    void commandIsRequired() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AgentRuntimeConfig(null, List.of(), Map.of(), null))
                .withMessage("command");
    }

    @Test
    void argsAndEnvDefaultToEmpty() {
        var config = new AgentRuntimeConfig("codex", null, null, null);
        assertThat(config.args()).isEmpty();
        assertThat(config.env()).isEmpty();
    }

    @Test
    void defensiveCopyOfArgs() {
        var args = new ArrayList<>(List.of("-p", "hello"));
        var config = new AgentRuntimeConfig("codex", args, Map.of(), null);
        args.add("mutated");
        assertThat(config.args()).hasSize(2);
    }

    @Test
    void defensiveCopyOfEnv() {
        var env = new HashMap<>(Map.of("KEY", "val"));
        var config = new AgentRuntimeConfig("codex", List.of(), env, null);
        env.put("EXTRA", "mutated");
        assertThat(config.env()).hasSize(1);
    }

    @Test
    void workingDirectoryIsOptional() {
        var config = new AgentRuntimeConfig("codex", List.of(), Map.of(), null);
        assertThat(config.workingDirectory()).isNull();

        var withDir = new AgentRuntimeConfig("codex", List.of(), Map.of(), Path.of("/tmp"));
        assertThat(withDir.workingDirectory()).isEqualTo(Path.of("/tmp"));
    }
}
