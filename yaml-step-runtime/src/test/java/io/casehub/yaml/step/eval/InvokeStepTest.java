package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.ResolvedStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InvokeStepTest {

    @Test
    void invoke_step_returns_error_result_not_exception() {
        PluginRegistry registry = new io.casehub.yaml.step.catalog.CompositePluginRegistry();
        var runner = new CatalogStepRunner(registry, new MapServiceRegistry());
        var invokeStep = new ResolvedStep.InvokeStep("test-invoke",
                Map.of("type", "mcp"), Map.of());

        Result result = runner.run(invokeStep, new VariableResolver(Map.of(), Set.of()));
        assertThat(result.isSuccess()).isFalse();
        assertThat(((Result.Failure) result).message()).contains("not yet supported");
    }
}
