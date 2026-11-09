package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.DataRealism;
import io.casehub.platform.simulation.Seedable;
import io.casehub.platform.simulation.SimulationCorpus;
import io.casehub.platform.simulation.SimulationStrategy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReferenceStrategy<I, O> implements SimulationStrategy<I, O> {

    private boolean seeded;

    public ReferenceStrategy(SimulationCorpus<I, O> corpus, String qualifiedName, Seedable seedable) {
        if (seedable != null && corpus.size(qualifiedName) > 0) {
            List<Map<String, Object>> outputs = new ArrayList<>();
            for (var record : corpus.list(qualifiedName)) {
                if (record.output() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) record.output();
                    outputs.add(map);
                } else if (record.output() != null) {
                    outputs.add(Map.of("_value", record.output()));
                }
            }
            if (!outputs.isEmpty()) {
                seedable.seed(qualifiedName, outputs);
                seeded = true;
            }
        }
    }

    @Override
    public O resolve(I input) {
        throw new UnsupportedOperationException("ReferenceStrategy delegates to real implementation");
    }

    @Override
    public boolean canResolve(I input) {
        return false;
    }

    @Override
    public DataRealism dataRealism() {
        return DataRealism.RECORDED_REAL;
    }

    public boolean isSeeded() {
        return seeded;
    }
}
