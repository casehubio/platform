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
            Instance<NlpTokenizerFactory> factories,
            @ConfigProperty(name = "casehub.template.language-families",
                            defaultValue = "") String languageFamiliesConfig) {
        var                           registry    = new LanguageFamilyRegistry();
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
        } else if (!languageFamiliesConfig.isBlank()) {
            for (String entry : languageFamiliesConfig.split(",")) {
                String[] parts = entry.strip().split("=", 2);
                if (parts.length == 2) {
                    String lang     = parts[0].strip();
                    String familyId = parts[1].strip();
                    if (!strategyMap.containsKey(familyId)) {
                        throw new IllegalStateException(
                                "Language '" + lang + "' mapped to unknown strategy family '"
                                + familyId + "'. Available: " + strategyMap.keySet());
                    }
                    registry.register(lang, familyId);
                }
            }
        } else {
            throw new IllegalStateException(
                    "Multiple MatchingStrategy beans found (" + strategyMap.keySet()
                    + ") but no casehub.template.language-families config provided. "
                    + "Set casehub.template.language-families=en=svo,fr=svo to map "
                    + "languages to strategy families.");
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
