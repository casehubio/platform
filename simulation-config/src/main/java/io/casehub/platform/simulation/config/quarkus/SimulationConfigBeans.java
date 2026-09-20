package io.casehub.platform.simulation.config.quarkus;

import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationCorpus;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.config.CompositeCorpusLoader;
import io.casehub.platform.simulation.config.CsvCorpusLoader;
import io.casehub.platform.simulation.config.DeclarativeExtractorFactory;
import io.casehub.platform.simulation.config.DeclarativeScorerFactory;
import io.casehub.platform.simulation.config.ParameterRegistry;
import io.casehub.platform.simulation.config.JsonCorpusLoader;
import io.casehub.platform.simulation.config.YamlCorpusLoader;
import io.casehub.platform.simulation.config.YamlSimulationConfig;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.InputStream;
import java.util.logging.Logger;

@ApplicationScoped
public class SimulationConfigBeans {

    private static final Logger LOG = Logger.getLogger(SimulationConfigBeans.class.getName());

    @Produces
    @ApplicationScoped
    public YamlSimulationConfig simulationConfig() {
        var mpConfig = ConfigProvider.getConfig();
        String configPath = mpConfig
                .getOptionalValue("casehub.simulation.config", String.class)
                .orElse(null);
        String defaultTenancyId = mpConfig
                .getOptionalValue("casehub.simulation.default-tenancy-id", String.class)
                .orElse(null);

        InputStream is = discoverYaml(configPath);
        if (is == null) {
            return new YamlSimulationConfig(
                    new java.io.ByteArrayInputStream(new byte[0]),
                    defaultTenancyId);
        }
        try (is) {
            return new YamlSimulationConfig(is, defaultTenancyId);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Failed to close simulation config stream", e);
        }
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

    @Produces
    @ApplicationScoped
    public CompositeCorpusLoader corpusLoader() {
        return new CompositeCorpusLoader(
                new YamlCorpusLoader(), new JsonCorpusLoader(), new CsvCorpusLoader());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void onStartup(@Observes StartupEvent event,
                   YamlSimulationConfig config,
                   SimulationCorpus corpus,
                   SimulationRuntime runtime) {

        String activeProfile = ConfigProvider.getConfig()
                .getOptionalValue("casehub.simulation.active-profile", String.class)
                .orElse(null);

        if (activeProfile != null) {
            config.loadAllCorpus(activeProfile).forEach(corpus::seed);
        } else {
            config.loadAllCorpus().forEach(corpus::seed);
        }

        runtime.setProfileSource(config);

        var registry = ParameterRegistry.loadFromClasspath();
        var factory = new DeclarativeExtractorFactory(registry);
        config.extractorSpecs()
                .forEach((qn, spec) -> runtime.registerExtractor(qn, factory.create(spec, qn)));

        var scorerFactory = new DeclarativeScorerFactory();
        config.scorerSpecs()
                .forEach((qn, spec) -> runtime.registerScorer(qn, scorerFactory.create(spec)));

        ConfigProvider.getConfig()
                .getOptionalValue("casehub.simulation.corpus.files", String.class)
                .ifPresent(v -> LOG.warning("casehub.simulation.corpus.files is retired. "
                        + "Move corpus file references into simulation.yaml's "
                        + "per-method corpus-files: key."));
    }

    private InputStream discoverYaml(String configPath) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (configPath != null) {
            if (configPath.startsWith("classpath:")) {
                return cl.getResourceAsStream(
                        configPath.substring("classpath:".length()));
            }
            try {
                return java.nio.file.Files.newInputStream(
                        java.nio.file.Path.of(configPath));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(
                        "Failed to open simulation config: " + configPath, e);
            }
        }
        InputStream is = cl.getResourceAsStream("simulation.yaml");
        if (is != null) return is;
        return cl.getResourceAsStream("simulation.yml");
    }
}
