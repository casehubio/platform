package io.casehub.platform.simulation;

@FunctionalInterface
public interface KeyExtractor<I> {

    String extract(I input);
}
