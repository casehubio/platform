package io.casehub.template.registry.mf2;

import io.casehub.template.registry.LanguageFamilyRegistry;
import io.casehub.template.registry.MatchingStrategy;
import io.casehub.template.registry.SelectorInfo;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.nlp.NlpTemplateRegistry;
import io.casehub.template.registry.nlp.svo.OpenNlpTokenizerFactory;
import io.casehub.template.registry.nlp.svo.SvoMatchingStrategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Mf2IntegrationTest {

    private NlpTemplateRegistry registry;

    @BeforeEach
    void setUp() {
        var tokenizerFactory = new OpenNlpTokenizerFactory();
        var familyRegistry = new LanguageFamilyRegistry();
        familyRegistry.register("en", "svo");
        familyRegistry.register("fr", "svo");

        var svoStrategy = new SvoMatchingStrategy();
        var strategies = Map.of("svo", (MatchingStrategy) svoStrategy);
        var resolver = new IcuMessageFormatResolver();

        registry = new NlpTemplateRegistry(tokenizerFactory, familyRegistry,
            strategies, 0.5, resolver);
    }

    @Test
    void matchThenGeneratePluralEnglish() {
        var def = TemplateDefinition.builder("item-count")
            .pattern("${count} items have been ${action}")
            .messageFormat(".input {$count :number}\n.match $count\n1 {{{$count} item has been {$action}}}\n* {{{$count} items have been {$action}}}")
            .build();
        var ns = new TemplateNamespace("protocol:work", Map.of("item-count", def));
        registry.register(ns);

        var match = (TemplateMatch.Matched) registry.match("protocol:work",
            "5 items have been approved");
        assertThat(match.fields()).containsEntry("count", "5");
        assertThat(match.fields()).containsEntry("action", "approved");

        String singular = registry.generate("protocol:work", "item-count",
            Map.of("count", "1", "action", "approved"));
        assertThat(singular).isEqualTo("1 item has been approved");

        String plural = registry.generate("protocol:work", "item-count",
            Map.of("count", "5", "action", "approved"));
        assertThat(plural).isEqualTo("5 items have been approved");
    }

    @Test
    void slotFillTemplateUnaffectedByMf2Module() {
        var def = TemplateDefinition.builder("vote")
            .pattern("I ${decision} this ${subject}")
            .build();
        var ns = new TemplateNamespace("protocol:review", Map.of("vote", def));
        registry.register(ns);

        String generated = registry.generate("protocol:review", "vote",
            Map.of("decision", "approve", "subject", "design"));
        assertThat(generated).isEqualTo("I approve this design");
    }

    @Test
    void schemaIncludesMf2Metadata() {
        var def = TemplateDefinition.builder("item-count")
            .pattern("${count} items")
            .messageFormat(".input {$count :integer}\n.match $count\n1 {{{$count} item}}\n* {{{$count} items}}")
            .build();
        var ns = new TemplateNamespace("test", Map.of("item-count", def));
        registry.register(ns);

        var schema = registry.schema("test", "item-count");
        assertThat(schema).isPresent();
        assertThat(schema.get().messageFormatSchema()).isNotNull();
        assertThat(schema.get().messageFormatSchema().selectors())
            .extracting(SelectorInfo::variableName)
            .contains("count");
    }

    @Test
    void genderSelectionThroughPipeline() {
        var def = TemplateDefinition.builder("join-msg")
            .pattern("${actor} joined the team")
            .messageFormat(".input {$gender :string}\n.match $gender\nmasculine {{He joined the team}}\nfeminine {{She joined the team}}\n* {{They joined the team}}")
            .build();
        var ns = new TemplateNamespace("test", Map.of("join-msg", def));
        registry.register(ns);

        assertThat(registry.generate("test", "join-msg", Map.of("gender", "masculine")))
            .isEqualTo("He joined the team");
        assertThat(registry.generate("test", "join-msg", Map.of("gender", "feminine")))
            .isEqualTo("She joined the team");
        assertThat(registry.generate("test", "join-msg", Map.of("gender", "other")))
            .isEqualTo("They joined the team");
    }
}
