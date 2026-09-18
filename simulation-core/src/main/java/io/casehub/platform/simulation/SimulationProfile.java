package io.casehub.platform.simulation;

public record SimulationProfile(SimulationConfig config,
                                SimulationCorpus<?, ?> corpus) {}
