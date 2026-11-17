package io.casehub.yaml.test;

import io.casehub.platform.simulation.MapSimulationConfig;
import io.casehub.platform.simulation.SimulationOverlay;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.config.YamlSimulationConfig;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import io.casehub.yaml.step.runner.PlaybookRunConfig;
import io.casehub.yaml.step.runner.PlaybookRunnerCore;
import io.casehub.yaml.step.runner.SimulationActivator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SimulationRuntimeActivator implements SimulationActivator {

    private final SimulationRuntime runtime;
    private final Path resourceRoot;

    public SimulationRuntimeActivator(SimulationRuntime runtime, Path resourceRoot) {
        this.runtime = runtime;
        this.resourceRoot = resourceRoot;
    }

    @Override
    public AutoCloseable activate(PlaybookRunConfig config, Path playbookPath) {
        SimulationOverlay overlay;
        if (config.corpusPath() != null) {
            Path corpusFile = resourceRoot.resolve(config.corpusPath());
            var parsed = SimulateBlockParser.parse(loadYamlMap(corpusFile), resourceRoot);
            overlay = runtime.pushOverlay(parsed.config(), parsed.corpus());
        } else {
            Path discovered = PlaybookRunnerCore.discoverSimulationYaml(playbookPath);
            if (discovered != null) {
                try (var is = Files.newInputStream(discovered)) {
                    var yamlConfig = new YamlSimulationConfig(is);
                    var corpus = new InMemorySimulationCorpus<>();
                    var allCorpus = yamlConfig.loadAllCorpus();
                    allCorpus.forEach(corpus::seed);
                    overlay = runtime.pushOverlay(yamlConfig, corpus);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to load discovered simulation.yaml: " + discovered, e);
                }
            } else {
                overlay = runtime.pushOverlay(MapSimulationConfig.builder().build());
            }
        }
        SimulationOverlay finalOverlay = overlay;
        return () -> runtime.popOverlay(finalOverlay);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> loadYamlMap(Path path) {
        try (var is = Files.newInputStream(path)) {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper(
                    new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
            return mapper.readValue(is, java.util.Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load: " + path, e);
        }
    }
}
