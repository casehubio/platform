package io.casehub.platform.simulation;

public record DriverFailure(int index, String label, Exception cause) {}
