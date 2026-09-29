package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.process.CommandPattern;
import io.casehub.platform.api.process.DefaultProcessExecutor;
import io.casehub.platform.api.process.ProcessExecutor;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptInvokeHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ProcessExecutor executor = new DefaultProcessExecutor();
    private final ScriptInvokeHandler handler = new ScriptInvokeHandler(mapper, executor);

    @Test
    void supportsScriptBindings() {
        assertThat(handler.supports(new InvokeBinding.Script("python3", "s.py", null, null, null))).isTrue();
        assertThat(handler.supports(new InvokeBinding.Script("node", "s.js", null, null, null))).isTrue();
        assertThat(handler.supports(new InvokeBinding.Mcp("test"))).isFalse();
    }

    @Test
    void createsNonNullAction() {
        var binding = new InvokeBinding.Script("python3", "steps/sentiment.py", null, null, null);
        var def = new Declaration("test", null, Map.of(), Map.of(), binding);
        assertThat(handler.create(def, binding)).isNotNull();
    }

    @Test
    void failsGracefullyOnMissingScript() {
        var binding = new InvokeBinding.Script("python3", "nonexistent-script.py", "5s", null, null);
        var def = new Declaration("test", null, Map.of(), Map.of(), binding);

        Action action = handler.create(def, binding);
        Result result = action.execute(Map.of("key", "value"), null);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void respectsAllowList() {
        var restricted = new DefaultProcessExecutor(List.of(
                new CommandPattern(List.of("echo"), CommandPattern.MatchMode.PREFIX)));
        var restrictedHandler = new ScriptInvokeHandler(mapper, restricted);
        var binding = new InvokeBinding.Script("python3", "script.py", null, null, null);
        var def = new Declaration("test", null, Map.of(), Map.of(), binding);

        Action action = restrictedHandler.create(def, binding);
        Result result = action.execute(Map.of(), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(((Result.Failure) result).message()).contains("not allowed");
    }

    @Test
    void usesConfiguredTimeout() {
        var binding = new InvokeBinding.Script("node", "script.js", "60s", null, null);
        assertThat(binding.timeout()).isEqualTo("60s");
    }
}
