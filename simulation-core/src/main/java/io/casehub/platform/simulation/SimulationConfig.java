package io.casehub.platform.simulation;

import java.util.Optional;

public interface SimulationConfig {

    Optional<String> strategyFor(String qualifiedName);

    boolean captureEnabled(String qualifiedName);

    Optional<ExhaustionPolicy> exhaustionPolicy(String qualifiedName);

    default Optional<MatchPolicy> matchPolicy(String qualifiedName) {
        return Optional.empty();
    }

    default Optional<Double> threshold(String qualifiedName) {
        return Optional.empty();
    }

    default double speed() {
        return 1.0;
    }

    default DataRealism fallthroughRealism(String qualifiedName) {
        return null;
    }

    default Optional<CorpusSelectorConfig> corpusSelection(String qualifiedName) {
        return Optional.empty();
    }

    default java.util.Set<String> corpusSelectionKeys() {
        return java.util.Set.of();
    }

    default java.util.Optional<String> corpusSelectionGroup(String qualifiedName) {
        return java.util.Optional.empty();
    }


    default java.util.Map<String, TriggerConfig> triggers() {
        return java.util.Map.of();
    }


}
