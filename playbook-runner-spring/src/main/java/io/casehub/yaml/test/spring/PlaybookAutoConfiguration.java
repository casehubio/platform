package io.casehub.yaml.test.spring;

import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.ServiceRegistry;
import io.casehub.yaml.test.PlaybookApi;
import io.casehub.yaml.test.PlaybookRunConfig;
import io.casehub.yaml.test.PlaybookRunRequest;
import io.casehub.yaml.test.PlaybookRunResult;
import io.casehub.yaml.test.FileSystemModuleLoader;
import io.casehub.yaml.test.PlaybookRunnerCore;
import io.casehub.yaml.test.PlaybookValidationResult;
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
        SimulationRuntime runtime = simulationRuntimeProvider.getIfAvailable();
        var moduleLoader = new FileSystemModuleLoader();
        return new PlaybookApi() {
            @Override
            public PlaybookRunResult run(PlaybookRunRequest request) {
                var config = new PlaybookRunConfig(request.dryRun(), request.vars(),
                        request.corpusPath(), request.simulationProfile());
                var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                        Path.of("."), runtime, moduleLoader);
                return runner.run(Path.of(request.playbookPath()), config);
            }

            @Override
            public PlaybookValidationResult validate(String playbookPath) {
                var runner = new PlaybookRunnerCore(pluginRegistry, serviceRegistry,
                        Path.of("."), null, moduleLoader);
                return runner.validate(Path.of(playbookPath));
            }
        };
    }
}
