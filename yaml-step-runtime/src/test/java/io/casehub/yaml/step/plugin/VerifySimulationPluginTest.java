package io.casehub.yaml.step.plugin;

import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.MapSimulationConfig;
import io.casehub.platform.simulation.SimulationOverlay;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VerifySimulationPluginTest {

    private final VerifySimulationPlugin plugin = new VerifySimulationPlugin();

    @Test
    void verifyCalledCountPasses() {
        var overlay = createOverlayWithInvocations("agent-provider.invoke", 2);
        var services = new MapServiceRegistry()
                .register(SimulationOverlay.class, overlay);

        var params = Map.<String, Object>of(
                "method", "agent-provider.invoke",
                "called", 2
        );

        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void verifyCalledCountFails() {
        var overlay = createOverlayWithInvocations("agent-provider.invoke", 1);
        var services = new MapServiceRegistry()
                .register(SimulationOverlay.class, overlay);

        var params = Map.<String, Object>of(
                "method", "agent-provider.invoke",
                "called", 3
        );

        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void verifyNeverCalledPasses() {
        var overlay = createOverlayWithInvocations("agent-provider.invoke", 0);
        var services = new MapServiceRegistry()
                .register(SimulationOverlay.class, overlay);

        var params = Map.<String, Object>of(
                "method", "agent-provider.invoke",
                "neverCalled", true
        );

        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void failsWithoutOverlay() {
        var services = new MapServiceRegistry();
        var params = Map.<String, Object>of("method", "test", "called", 1);

        Result result = plugin.execute(params, services);
        assertThat(result.isSuccess()).isFalse();
        assertThat(((Result.Failure) result).message()).contains("overlay");
    }

    private SimulationOverlay createOverlayWithInvocations(String qn, int count) {
        var config = MapSimulationConfig.of(Map.of(qn, "key"));
        var corpus = new InMemorySimulationCorpus<Object, Object>();
        var runtime = new SimulationRuntime(config, corpus);
        var overlay = runtime.pushOverlay(config, corpus);

        for (int i = 0; i < count; i++) {
            runtime.recordJournal(qn, "default",
                    "input-" + i, "output-" + i,
                    io.casehub.platform.simulation.DataRealism.DOMAIN_PLAUSIBLE);
        }

        return overlay;
    }
}
