package io.casehub.template.registry.nlp;

import io.casehub.template.registry.LanguageFamilyRegistry;
import io.casehub.template.registry.MatchingStrategy;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.nlp.svo.OpenNlpTokenizerFactory;
import io.casehub.template.registry.nlp.svo.SvoMatchingStrategy;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultiLanguageRegistryTest {

    private NlpTemplateRegistry registry;

    @BeforeEach
    void setUp() {
        var factory = new OpenNlpTokenizerFactory();
        var strategy = new SvoMatchingStrategy();
        var familyRegistry = new LanguageFamilyRegistry();
        familyRegistry.register("en", "svo");
        familyRegistry.register("fr", "svo");
        registry = new NlpTemplateRegistry(factory, familyRegistry,
                Map.of("svo", strategy), 0.5);
    }

    @Test
    void englishNamespaceMatchesEnglishInput() {
        var vote = TemplateDefinition.builder("vote")
                .pattern("I ${decision} this ${subject}")
                .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                .variable("subject", VariableConstraint.string())
                .build();
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("vote", vote);
        registry.register(new TemplateNamespace("protocol:en", templates, "en"));

        var result = registry.match("protocol:en", "I approve this design");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        var matched = (TemplateMatch.Matched) result;
        assertThat(matched.fields()).containsEntry("decision", "approve");
    }

    @Test
    void frenchNamespaceMatchesFrenchInput() {
        var vote = TemplateDefinition.builder("vote")
                .pattern("Je ${decision} ce ${subject}")
                .variable("decision", VariableConstraint.enumOf("approuve", "rejette"))
                .variable("subject", VariableConstraint.string())
                .build();
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("vote", vote);
        registry.register(new TemplateNamespace("protocol:fr", templates, "fr"));

        var result = registry.match("protocol:fr", "Je approuve ce document");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        var matched = (TemplateMatch.Matched) result;
        assertThat(matched.fields()).containsEntry("decision", "approuve");
    }

    @Test
    void crossNamespaceMatchWithMixedLanguages() {
        var voteEn = TemplateDefinition.builder("vote")
                .pattern("I ${decision} this ${subject}")
                .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                .variable("subject", VariableConstraint.string())
                .build();
        var voteFr = TemplateDefinition.builder("vote")
                .pattern("Je ${decision} ce ${subject}")
                .variable("decision", VariableConstraint.enumOf("approuve", "rejette"))
                .variable("subject", VariableConstraint.string())
                .build();

        var enTemplates = new LinkedHashMap<String, TemplateDefinition>();
        enTemplates.put("vote", voteEn);
        registry.register(new TemplateNamespace("protocol:en", enTemplates, "en"));

        var frTemplates = new LinkedHashMap<String, TemplateDefinition>();
        frTemplates.put("vote", voteFr);
        registry.register(new TemplateNamespace("protocol:fr", frTemplates, "fr"));

        var enResult = registry.match("I approve this design");
        assertThat(enResult).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(((TemplateMatch.Matched) enResult).namespace()).isEqualTo("protocol:en");

        var frResult = registry.match("Je approuve ce document");
        assertThat(frResult).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(((TemplateMatch.Matched) frResult).namespace()).isEqualTo("protocol:fr");
    }

    @Test
    void unsupportedLanguageFailsFast() {
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("test", TemplateDefinition.builder("test")
                .pattern("test ${x}")
                .variable("x", VariableConstraint.string())
                .build());
        assertThatThrownBy(() ->
                registry.register(new TemplateNamespace("protocol:de", templates, "de")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("de");
    }
}
