package io.casehub.yaml.test.quarkus;

import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.ServiceRegistry;
import io.casehub.yaml.test.PlaybookApi;
import io.casehub.yaml.test.PlaybookRunConfig;
import io.casehub.yaml.test.PlaybookRunRequest;
import io.casehub.yaml.test.PlaybookRunResult;
import io.casehub.yaml.test.PlaybookRunnerCore;
import io.casehub.yaml.test.PlaybookValidationResult;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.nio.file.Path;

@ApplicationScoped
@McpDomain("playbooks")
public class PlaybookBeans implements PlaybookApi {

    private final PluginRegistry pluginRegistry;
    private final ServiceRegistry serviceRegistry;
    private final SimulationRuntime simulationRuntime;

    @Inject
    public PlaybookBeans(PluginRegistry pluginRegistry,
                         ServiceRegistry serviceRegistry,
                         Instance<SimulationRuntime> simulationRuntimeInstance) {
        this.pluginRegistry = pluginRegistry;
        this.serviceRegistry = serviceRegistry;
        this.simulationRuntime = simulationRuntimeInstance.isResolvable()
                ? simulationRuntimeInstance.get() : null;
    }

    @Override
    @PlatformMutation("Run a playbook with optional dry-run simulation")
    public PlaybookRunResult run(PlaybookRunRequest request) {
        var config = new PlaybookRunConfig(request.dryRun(), request.vars(),
                request.corpusPath(), request.simulationProfile());
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                Path.of("."), simulationRuntime);
        return runner.run(Path.of(request.playbookPath()), config);
    }

    @Override
    @PlatformQuery("Validate a playbook structure without executing")
    public PlaybookValidationResult validate(String playbookPath) {
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                Path.of("."), null);
        return runner.validate(Path.of(playbookPath));
    }
}
