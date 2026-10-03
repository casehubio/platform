package io.casehub.platform.api.model;

import java.util.Arrays;
import java.util.List;

public record ModelChain(List<ModelChainEntry> entries) {

    public ModelChain {
        entries = List.copyOf(entries);
    }

    public static ModelChain of(String... modelRefs) {
        return new ModelChain(
            Arrays.stream(modelRefs)
                  .map(ModelChainEntry.Named::new)
                  .<ModelChainEntry>map(e -> e)
                  .toList());
    }

    public static ModelChain of(List<ModelChainEntry> entries) {
        return new ModelChain(entries);
    }

    public boolean isEmpty() { return entries.isEmpty(); }

    public sealed interface ModelChainEntry {
        record Named(String modelRef) implements ModelChainEntry {}
        record Queried(ModelQuery query) implements ModelChainEntry {}
    }
}
