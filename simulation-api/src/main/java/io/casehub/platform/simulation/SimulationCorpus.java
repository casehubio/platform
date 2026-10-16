package io.casehub.platform.simulation;

import java.util.List;
import java.util.Optional;

public interface SimulationCorpus<I, O> {

    Optional<O> lookupByKey(String qualifiedName, String key);

    default List<O> lookupAllByKey(String qualifiedName, String key) {
        return list(qualifiedName).stream()
                .filter(r -> key.equals(r.key()))
                .map(InvocationRecord::output)
                .toList();
    }

    Optional<O> lookupByIndex(String qualifiedName, int index);

    List<InvocationRecord<I, O>> list(String qualifiedName);

    List<InvocationRecord<I, O>> listByTenant(String qualifiedName, String tenancyId);

    void record(String qualifiedName, String tenancyId, I input, O output);

    void record(String qualifiedName, String tenancyId, String key, I input, O output);

    void seed(String qualifiedName, List<InvocationRecord<I, O>> records);

    void clear(String qualifiedName);

    int size(String qualifiedName);
}
