package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.SimulationCorpus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ListBackedCorpus<I, O> implements SimulationCorpus<I, O> {

    private final List<InvocationRecord<I, O>> records = new ArrayList<>();

    @SafeVarargs
    static <I, O> ListBackedCorpus<I, O> of(final InvocationRecord<I, O>... entries) {
        final var corpus = new ListBackedCorpus<I, O>();
        for (final var entry : entries) {
            corpus.records.add(entry);
        }
        return corpus;
    }

    static <I, O> InvocationRecord<I, O> entry(final String key, final I input, final O output) {
        return new InvocationRecord<>("test-tenant", key, input, output, Instant.now());
    }

    @Override
    public Optional<O> lookupByKey(final String qualifiedName, final String key) {
        return records.stream()
                .filter(r -> key.equals(r.key()))
                .map(InvocationRecord::output)
                .findFirst();
    }

    @Override
    public Optional<O> lookupByIndex(final String qualifiedName, final int index) {
        if (index < 0 || index >= records.size()) {
            return Optional.empty();
        }
        return Optional.of(records.get(index).output());
    }

    @Override
    public List<InvocationRecord<I, O>> list(final String qualifiedName) {
        return List.copyOf(records);
    }

    @Override
    public List<InvocationRecord<I, O>> listByTenant(final String qualifiedName, final String tenancyId) {
        return records.stream().filter(r -> tenancyId.equals(r.tenancyId())).toList();
    }

    @Override
    public void record(final String qualifiedName, final String tenancyId, final I input, final O output) {
        records.add(new InvocationRecord<>(tenancyId, null, input, output, Instant.now()));
    }

    @Override
    public void record(final String qualifiedName, final String tenancyId, final String key, final I input, final O output) {
        records.add(new InvocationRecord<>(tenancyId, key, input, output, Instant.now()));
    }

    @Override
    public void seed(final String qualifiedName, final List<InvocationRecord<I, O>> newRecords) {
        records.addAll(newRecords);
    }

    @Override
    public void clear(final String qualifiedName) {
        records.clear();
    }

    @Override
    public int size(final String qualifiedName) {
        return records.size();
    }
}
