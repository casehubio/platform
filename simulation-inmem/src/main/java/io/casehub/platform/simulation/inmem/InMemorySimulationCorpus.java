package io.casehub.platform.simulation.inmem;

import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.SimulationCorpus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemorySimulationCorpus<I, O> implements SimulationCorpus<I, O> {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<InvocationRecord<I, O>>> store =
            new ConcurrentHashMap<>();

    @Override
    public Optional<O> lookupByKey(final String qualifiedName, final String key) {
        final var records = store.get(qualifiedName);
        if (records == null) {
            return Optional.empty();
        }
        return records.stream()
                .filter(r -> key.equals(r.key()))
                .map(InvocationRecord::output)
                .findFirst();
    }

    @Override
    public Optional<O> lookupByIndex(final String qualifiedName, final int index) {
        final var records = store.get(qualifiedName);
        if (records == null || index < 0 || index >= records.size()) {
            return Optional.empty();
        }
        return Optional.of(records.get(index).output());
    }

    @Override
    public List<InvocationRecord<I, O>> list(final String qualifiedName) {
        final var records = store.get(qualifiedName);
        if (records == null) {
            return List.of();
        }
        return List.copyOf(records);
    }

    @Override
    public List<InvocationRecord<I, O>> listByTenant(final String qualifiedName, final String tenancyId) {
        final var records = store.get(qualifiedName);
        if (records == null) {
            return List.of();
        }
        return records.stream()
                .filter(r -> tenancyId.equals(r.tenancyId()))
                .toList();
    }

    @Override
    public void record(final String qualifiedName, final String tenancyId, final I input, final O output) {
        store.computeIfAbsent(qualifiedName, k -> new CopyOnWriteArrayList<>())
                .add(new InvocationRecord<>(tenancyId, null, input, output, Instant.now()));
    }

    @Override
    public void record(final String qualifiedName, final String tenancyId, final String key,
                       final I input, final O output) {
        store.computeIfAbsent(qualifiedName, k -> new CopyOnWriteArrayList<>())
                .add(new InvocationRecord<>(tenancyId, key, input, output, Instant.now()));
    }

    @Override
    public void seed(final String qualifiedName, final List<InvocationRecord<I, O>> records) {
        store.computeIfAbsent(qualifiedName, k -> new CopyOnWriteArrayList<>())
                .addAll(records);
    }

    @Override
    public void clear(final String qualifiedName) {
        store.remove(qualifiedName);
    }

    @Override
    public int size(final String qualifiedName) {
        final var records = store.get(qualifiedName);
        return records == null ? 0 : records.size();
    }
}
