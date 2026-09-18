package io.casehub.platform.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class CorpusSeed<I, O> {

    private final String qualifiedName;
    private final String defaultTenancyId;
    private final List<InvocationRecord<I, O>> records = new ArrayList<>();
    private KeyExtractor<I> keyExtractor;
    private Function<I, O> outputMapper;

    public CorpusSeed(String qualifiedName, String defaultTenancyId) {
        this.qualifiedName = Objects.requireNonNull(qualifiedName);
        this.defaultTenancyId = Objects.requireNonNull(defaultTenancyId);
    }

    public CorpusSeed<I, O> withKeyExtractor(KeyExtractor<I> extractor) {
        this.keyExtractor = extractor;
        return this;
    }

    public CorpusSeed<I, O> withOutputMapper(Function<I, O> mapper) {
        this.outputMapper = mapper;
        return this;
    }

    public CorpusSeed<I, O> add(I input, O output) {
        String key = keyExtractor != null ? keyExtractor.extract(input) : null;
        records.add(InvocationRecord.of(defaultTenancyId, key, input, output));
        return this;
    }

    public CorpusSeed<I, O> add(String key, I input, O output) {
        records.add(InvocationRecord.of(defaultTenancyId, key, input, output));
        return this;
    }

    public CorpusSeed<I, O> add(String tenancyId, String key, I input, O output) {
        records.add(InvocationRecord.of(tenancyId, key, input, output));
        return this;
    }

    public CorpusSeed<I, O> add(I input) {
        if (outputMapper == null) {
            throw new IllegalStateException(
                    "add(input) requires withOutputMapper() — call add(input, output) instead");
        }
        return add(input, outputMapper.apply(input));
    }

    public void seedInto(SimulationCorpus<I, O> corpus) {
        corpus.seed(qualifiedName, List.copyOf(records));
    }

    public List<InvocationRecord<I, O>> build() {
        return List.copyOf(records);
    }

    public String qualifiedName() {
        return qualifiedName;
    }

    public KeyExtractor<I> keyExtractor() {
        return keyExtractor;
    }
}
