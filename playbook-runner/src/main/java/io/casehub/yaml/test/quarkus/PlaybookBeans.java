package io.casehub.yaml.test.quarkus;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.ServiceRegistry;
import io.casehub.yaml.test.FileSystemModuleLoader;
import io.casehub.yaml.test.PlaybookApi;
import io.casehub.yaml.test.PlaybookRunConfig;
import io.casehub.yaml.test.PlaybookRunRequest;
import io.casehub.yaml.test.PlaybookRunResult;
import io.casehub.yaml.test.PlaybookRunnerCore;
import io.casehub.yaml.test.PlaybookValidationResult;
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
    private final FileSystemModuleLoader moduleLoader = new FileSystemModuleLoader();

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
        Path resourceRoot = PlaybookRunnerCore.resolveClasspathRoot("playbooks");
        if (resourceRoot == null) {
            resourceRoot = Path.of(".");
        }
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                            resourceRoot, simulationRuntime, moduleLoader);
        return runner.run(Path.of(request.playbookPath()), config);
    }

    @Override
    @PlatformQuery("Validate a playbook structure without executing")
    public PlaybookValidationResult validate(String playbookPath) {
        Path resourceRoot = PlaybookRunnerCore.resolveClasspathRoot("playbooks");
        if (resourceRoot == null) {
            resourceRoot = Path.of(".");
        }
        var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                            resourceRoot, null, moduleLoader);
        return runner.validate(Path.of(playbookPath));
    }
}
