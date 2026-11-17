package io.casehub.yaml.test.spring;

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
import io.casehub.yaml.test.SimulationRuntimeActivator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

@AutoConfiguration
@ConditionalOnBean(PluginRegistry.class)
public class PlaybookAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PlaybookApi.class)
    public PlaybookApi playbookApi(PluginRegistry pluginRegistry,
                                   ServiceRegistry serviceRegistry,
                                   ObjectProvider<SimulationRuntime> simulationRuntimeProvider) {
        SimulationRuntime runtime      = simulationRuntimeProvider.getIfAvailable();
        SimulationActivator activator  = runtime != null ? new SimulationRuntimeActivator(runtime, Path.of(".")) : null;
        var               moduleLoader = new FileSystemModuleLoader();
        return new PlaybookApi() {
            private Path resourceRoot() {
                Path root = PlaybookRunnerCore.resolveClasspathRoot("playbooks");
                return root != null ? root : Path.of(".");
            }

            @Override
            public PlaybookRunResult run(PlaybookRunRequest request) {
                var config = new PlaybookRunConfig(request.dryRun(), request.vars(),
                                                   request.corpusPath(), request.simulationProfile());
                var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                                    resourceRoot(), activator, moduleLoader);
                return runner.run(Path.of(request.playbookPath()), config);
            }

            @Override
            public PlaybookValidationResult validate(String playbookPath) {
                var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                                                    resourceRoot(), null, moduleLoader);
                return runner.validate(Path.of(playbookPath));
            }
        };
    }
}
