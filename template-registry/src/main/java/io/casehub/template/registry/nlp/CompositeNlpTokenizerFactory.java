package io.casehub.template.registry.nlp;

import io.casehub.template.registry.NlpTokenizer;
import io.casehub.template.registry.NlpTokenizerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CompositeNlpTokenizerFactory implements NlpTokenizerFactory {

    private final List<NlpTokenizerFactory> delegates;

    public CompositeNlpTokenizerFactory(Iterable<NlpTokenizerFactory> factories) {
        Objects.requireNonNull(factories, "factories");
        var list = new java.util.ArrayList<NlpTokenizerFactory>();
        factories.forEach(list::add);
        this.delegates = List.copyOf(list);
    }

    @Override
    public NlpTokenizer create(String languageTag) {
        for (NlpTokenizerFactory factory : delegates) {
            if (factory.supportedLanguages().contains(languageTag)) {
                return factory.create(languageTag);
            }
        }
        throw new UnsupportedOperationException(
                "No tokenizer factory supports language '" + languageTag
                + "'. Available: " + supportedLanguages());
    }

    @Override
    public Set<String> supportedLanguages() {
        Set<String> all = new LinkedHashSet<>();
        for (NlpTokenizerFactory factory : delegates) {
            all.addAll(factory.supportedLanguages());
        }
        return Set.copyOf(all);
    }
}
