package io.casehub.yaml.test.quarkus;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.ServiceRegistry;
import io.casehub.yaml.step.runner.FileSystemModuleLoader;
import io.casehub.yaml.step.runner.PlaybookApi;
import io.casehub.yaml.step.runner.PlaybookRunConfig;
import io.casehub.yaml.step.runner.PlaybookRunRequest;
import io.casehub.yaml.step.runner.PlaybookRunResult;
import io.casehub.yaml.step.runner.PlaybookRunnerCore;
import io.casehub.yaml.step.runner.PlaybookValidationResult;
import io.casehub.yaml.step.runner.SimulationActivator;
import io.casehub.yaml.step.runner.UnmetRequirementHandler;
import io.casehub.yaml.step.runner.UserInputProvider;
import io.casehub.yaml.test.SimulationRuntimeActivator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
@McpDomain("playbooks")
public class PlaybookBeans implements PlaybookApi {

    private final PluginRegistry pluginRegistry;
    private final ServiceRegistry serviceRegistry;
    private final SimulationActivator simulationActivator;
    private final UserInputProvider userInputProvider;
    private final FileSystemModuleLoader moduleLoader = new FileSystemModuleLoader();

    @Inject
    public PlaybookBeans(PluginRegistry pluginRegistry,
                         ServiceRegistry serviceRegistry,
                         Instance<SimulationRuntime> simulationRuntimeInstance,
                         Instance<UserInputProvider> userInputProviderInstance) {
        this.pluginRegistry = pluginRegistry;
        this.serviceRegistry = serviceRegistry;
        SimulationRuntime runtime = simulationRuntimeInstance.isResolvable()
                ? simulationRuntimeInstance.get() : null;
        this.simulationActivator = runtime != null
                ? new SimulationRuntimeActivator(runtime, Path.of(".")) : null;
        this.userInputProvider = userInputProviderInstance.isResolvable()
                ? userInputProviderInstance.get() : null;
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
                                            resourceRoot, simulationActivator, moduleLoader,
                                            null, List.of(new UnmetRequirementHandler()),
                                            userInputProvider);
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
