package io.casehub.yaml.step.runner;

import java.nio.file.Path;

@FunctionalInterface
public interface SimulationActivator {
    AutoCloseable activate(PlaybookRunConfig config, Path playbookPath);
}
