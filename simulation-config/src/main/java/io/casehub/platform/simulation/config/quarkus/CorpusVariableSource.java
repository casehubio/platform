package io.casehub.platform.simulation.config.quarkus;

import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.SimulationCorpus;
import io.casehub.yaml.core.resolver.ObjectVariableSource;

import java.util.List;

public class CorpusVariableSource implements ObjectVariableSource {

    private final SimulationCorpus<?, ?> corpus;

    public CorpusVariableSource(SimulationCorpus<?, ?> corpus) {
        this.corpus = corpus;
    }

    @Override
    public Object resolve(String name) {
        int bracket = name.indexOf('[');
        if (bracket >= 0) {
            String qualifiedName = name.substring(0, bracket);
            int closeBracket = name.indexOf(']', bracket);
            if (closeBracket < 0) return null;
            int index = Integer.parseInt(name.substring(bracket + 1, closeBracket));
            List<?> items = listInputs(qualifiedName);
            if (items == null || index >= items.size()) return null;
            return items.get(index);
        }
        return listInputs(name);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<?> listInputs(String qualifiedName) {
        List<? extends InvocationRecord> records = corpus.list(qualifiedName);
        if (records == null || records.isEmpty()) return null;
        return records.stream().map(InvocationRecord::input).toList();
    }
}
