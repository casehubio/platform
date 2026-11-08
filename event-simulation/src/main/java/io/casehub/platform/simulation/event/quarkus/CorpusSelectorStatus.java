package io.casehub.platform.simulation.event.quarkus;

public record CorpusSelectorStatus(
        String qualifiedName,
        String type,
        String activePhase) {}
