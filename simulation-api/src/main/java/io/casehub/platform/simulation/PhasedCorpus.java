package io.casehub.platform.simulation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PhasedCorpus<I, O> implements SimulationCorpus<I, O> {

    private final CorpusSelector selector;
    private final Map<String, List<InvocationRecord<I, O>>> phases;

    public PhasedCorpus(CorpusSelector selector, Map<String, List<InvocationRecord<I, O>>> phases) {
        this.selector = selector;
        this.phases = Map.copyOf(phases);
    }

    @Override
    public Optional<O> lookupByKey(String qualifiedName, String key) {
        return activeEntries(qualifiedName).stream()
                .filter(r -> key.equals(r.key()))
                .map(InvocationRecord::output)
                .findFirst();
    }

    @Override
    public List<O> lookupAllByKey(String qualifiedName, String key) {
        return activeEntries(qualifiedName).stream()
                .filter(r -> key.equals(r.key()))
                .map(InvocationRecord::output)
                .toList();
    }

    @Override
    public Optional<O> lookupByIndex(String qualifiedName, int index) {
        var entries = activeEntries(qualifiedName);
        if (index < 0 || index >= entries.size()) return Optional.empty();
        return Optional.of(entries.get(index).output());
    }

    @Override
    public List<InvocationRecord<I, O>> list(String qualifiedName) {
        return activeEntries(qualifiedName);
    }

    @Override
    public List<InvocationRecord<I, O>> listByTenant(String qualifiedName, String tenancyId) {
        return activeEntries(qualifiedName).stream()
                .filter(r -> tenancyId.equals(r.tenancyId()))
                .toList();
    }

    @Override
    public int size(String qualifiedName) {
        return activeEntries(qualifiedName).size();
    }

    @Override
    public void record(String qualifiedName, String tenancyId, I input, O output) {
        throw new UnsupportedOperationException("PhasedCorpus is read-only");
    }

    @Override
    public void record(String qualifiedName, String tenancyId, String key, I input, O output) {
        throw new UnsupportedOperationException("PhasedCorpus is read-only");
    }

    @Override
    public void seed(String qualifiedName, List<InvocationRecord<I, O>> records) {
        throw new UnsupportedOperationException("PhasedCorpus is read-only");
    }

    @Override
    public void clear(String qualifiedName) {
        throw new UnsupportedOperationException("PhasedCorpus is read-only");
    }

    private List<InvocationRecord<I, O>> activeEntries(String qualifiedName) {
        String phase = selector.activePhase();
        List<InvocationRecord<I, O>> entries = phases.get(phase);
        if (entries == null) {
            throw new SimulationConfigException(
                    "Phase '" + phase + "' not found in PhasedCorpus for " + qualifiedName
                    + " — available phases: " + phases.keySet());
        }
        return entries;
    }
}
