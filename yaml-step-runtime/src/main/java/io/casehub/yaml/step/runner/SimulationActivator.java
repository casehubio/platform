package io.casehub.yaml.step.runner;

import java.nio.file.Path;

public interface SimulationActivator {
    AutoCloseable activate(PlaybookRunConfig config, Path playbookPath);

    default java.util.function.Predicate<String> simulationChecker() {
        return null;
    }

    default java.util.function.Consumer<String> stepCompletionNotifier() {
        return null;
    }
}
