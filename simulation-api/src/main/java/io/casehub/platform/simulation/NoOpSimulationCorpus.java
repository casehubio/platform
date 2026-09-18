package io.casehub.platform.simulation;

import java.util.List;
import java.util.Optional;

public class NoOpSimulationCorpus<I, O> implements SimulationCorpus<I, O> {

    @Override
    public Optional<O> lookupByKey(final String qualifiedName, final String key) {
        return Optional.empty();
    }

    @Override
    public Optional<O> lookupByIndex(final String qualifiedName, final int index) {
        return Optional.empty();
    }

    @Override
    public List<InvocationRecord<I, O>> list(final String qualifiedName) {
        return List.of();
    }

    @Override
    public List<InvocationRecord<I, O>> listByTenant(final String qualifiedName, final String tenancyId) {
        return List.of();
    }

    @Override
    public void record(final String qualifiedName, final String tenancyId, final I input, final O output) {
    }

    @Override
    public void record(final String qualifiedName, final String tenancyId, final String key, final I input, final O output) {
    }

    @Override
    public void seed(final String qualifiedName, final List<InvocationRecord<I, O>> records) {
    }

    @Override
    public void clear(final String qualifiedName) {
    }

    @Override
    public int size(final String qualifiedName) {
        return 0;
    }
}
