package io.casehub.platform.simulation;

@FunctionalInterface
public interface TemporalEventSink<E> {
    void deliver(String qualifiedName, String label, E event);
}
