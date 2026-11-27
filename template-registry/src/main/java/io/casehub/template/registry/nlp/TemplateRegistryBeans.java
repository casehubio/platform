package io.casehub.template.registry.nlp;

import io.casehub.template.registry.LanguageFamilyRegistry;
import io.casehub.template.registry.MatchingStrategy;
import io.casehub.template.registry.MessageFormatResolver;
import io.casehub.template.registry.NlpTokenizerFactory;
import io.casehub.template.registry.UnifiedTemplateRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class TemplateRegistryBeans {

    @Produces
    @ApplicationScoped
    public LanguageFamilyRegistry languageFamilyRegistry(
            Instance<MatchingStrategy> strategies,
            Instance<NlpTokenizerFactory> factories) {
        var registry = new LanguageFamilyRegistry();
        Map<String, MatchingStrategy> strategyMap = new LinkedHashMap<>();
        for (MatchingStrategy strategy : strategies) {
            strategyMap.put(strategy.familyId(), strategy);
        }
        if (strategyMap.size() == 1) {
            String familyId = strategyMap.keySet().iterator().next();
            for (NlpTokenizerFactory factory : factories) {
                for (String lang : factory.supportedLanguages()) {
                    registry.register(lang, familyId);
                }
            }
        } else {
            for (NlpTokenizerFactory factory : factories) {
                for (String lang : factory.supportedLanguages()) {
                    for (var entry : strategyMap.entrySet()) {
                        registry.register(lang, entry.getKey());
                    }
                }
            }
        }
        return registry;
    }

    @Produces
    @ApplicationScoped
    public UnifiedTemplateRegistry nlpTemplateRegistry(
            Instance<NlpTokenizerFactory> factories,
            Instance<MatchingStrategy> strategies,
            LanguageFamilyRegistry familyRegistry,
            Instance<MessageFormatResolver> resolverInstance,
            @ConfigProperty(name = "casehub.template.score-threshold",
                            defaultValue = "0.5") double scoreThreshold) {
        NlpTokenizerFactory tokenizerFactory = new CompositeNlpTokenizerFactory(factories);
        Map<String, MatchingStrategy> strategyMap = new LinkedHashMap<>();
        for (MatchingStrategy strategy : strategies) {
            strategyMap.put(strategy.familyId(), strategy);
        }
        MessageFormatResolver resolver = resolverInstance.isResolvable()
                ? resolverInstance.get() : null;
        return new NlpTemplateRegistry(tokenizerFactory, familyRegistry,
                strategyMap, scoreThreshold, resolver);
    }
}
