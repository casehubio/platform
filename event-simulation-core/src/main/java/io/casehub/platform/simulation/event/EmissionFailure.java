package io.casehub.platform.simulation.event;

public record EmissionFailure(String qualifiedName, Exception cause) {}
