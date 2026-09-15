package io.casehub.platform.simulation.config.quarkus;

import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationCorpus;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import io.casehub.platform.simulation.config.DeclarativeExtractorFactory;
import io.casehub.platform.simulation.config.DeclarativeScorerFactory;
import io.casehub.platform.simulation.config.SmallRyeSimulationConfig;
import io.casehub.platform.simulation.config.YamlCorpusLoader;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SimulationConfigBeans {

    @Produces
    @ApplicationScoped
    public SimulationConfig simulationConfig() {
        return new SmallRyeSimulationConfig(ConfigProvider.getConfig());
    }

    @Produces
    @ApplicationScoped
    @SuppressWarnings({"rawtypes", "unchecked"})
    public SimulationCorpus simulationCorpus() {
        return new InMemorySimulationCorpus<>();
    }

    @Produces
    @ApplicationScoped
    @SuppressWarnings({"rawtypes", "unchecked"})
    public SimulationRuntime simulationRuntime(SimulationConfig config,
                                               SimulationCorpus corpus) {
        return new SimulationRuntime(config, corpus);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void onStartup(@Observes StartupEvent event,
                   SimulationCorpus corpus,
                   SimulationRuntime runtime,
                   @ConfigProperty(name = "casehub.simulation.corpus.files")
                   Optional<List<String>> corpusFiles) {
        if (corpusFiles.isPresent() && !corpusFiles.get().isEmpty()) {
            var loader = new YamlCorpusLoader();
            var loaded = loader.loadFromPaths(corpusFiles.get());
            loaded.forEach(corpus::seed);
        }

        var extractorConfig = new SmallRyeSimulationConfig(ConfigProvider.getConfig());
        var factory = new DeclarativeExtractorFactory();
        extractorConfig.extractorSpecs()
                .forEach((qn, spec) -> runtime.registerExtractor(qn, factory.create(spec)));

        var scorerFactory = new DeclarativeScorerFactory();
        extractorConfig.scorerSpecs()
                .forEach((qn, spec) -> runtime.registerScorer(qn, scorerFactory.create(spec)));
    }
}
