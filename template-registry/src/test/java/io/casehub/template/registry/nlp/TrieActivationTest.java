package io.casehub.template.registry.nlp;

import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrieActivationTest {

    private final OpenNlpTokenizer tokenizer = new OpenNlpTokenizer();

    @Test
    void belowThreshold_matchesCorrectly() {
        var registry = new NlpTemplateRegistry(tokenizer, 0.3, 0.5, 100);
        registry.register(buildNamespace("test-ns", 3));

        TemplateMatch result = registry.match("test-ns", "Cancel my order");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(((TemplateMatch.Matched) result).templateName()).isEqualTo("cancel");
    }

    @Test
    void aboveThreshold_matchesCorrectly() {
        var registry = new NlpTemplateRegistry(tokenizer, 0.3, 0.5, 2);
        registry.register(buildNamespace("test-ns", 3));

        TemplateMatch result = registry.match("test-ns", "Cancel my order");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(((TemplateMatch.Matched) result).templateName()).isEqualTo("cancel");
    }

    @Test
    void trieAndAffinity_produceSameMatchResults() {
        var templates = buildNamespace("test-ns", 5);
        var inputs = List.of(
                "Cancel my order",
                "Submit the report",
                "Review the document",
                "The weather is nice today"
        );

        var affinityRegistry = new NlpTemplateRegistry(tokenizer, 0.3, 0.5, 100);
        affinityRegistry.register(templates);

        var trieRegistry = new NlpTemplateRegistry(tokenizer, 0.3, 0.5, 2);
        trieRegistry.register(templates);

        for (String input : inputs) {
            TemplateMatch affinityResult = affinityRegistry.match("test-ns", input);
            TemplateMatch trieResult = trieRegistry.match("test-ns", input);

            if (affinityResult instanceof TemplateMatch.Matched am) {
                assertThat(trieResult).isInstanceOf(TemplateMatch.Matched.class);
                assertThat(((TemplateMatch.Matched) trieResult).templateName())
                        .isEqualTo(am.templateName());
            } else {
                assertThat(trieResult).isNotInstanceOf(TemplateMatch.Matched.class);
            }
        }
    }

    @Test
    void deregister_clearsTrie() {
        var registry = new NlpTemplateRegistry(tokenizer, 0.3, 0.5, 2);
        registry.register(buildNamespace("test-ns", 3));

        TemplateMatch before = registry.match("test-ns", "Cancel my order");
        assertThat(before).isInstanceOf(TemplateMatch.Matched.class);

        registry.deregister("test-ns");

        TemplateMatch after = registry.match("Cancel my order");
        assertThat(after).isInstanceOf(TemplateMatch.Unmatched.class);
    }

    @Test
    void replaceOrRegister_rebuildsTrie() {
        var registry = new NlpTemplateRegistry(tokenizer, 0.3, 0.5, 2);
        registry.register(buildNamespace("test-ns", 3));

        var newTemplates = new LinkedHashMap<String, TemplateDefinition>();
        newTemplates.put("hello", new TemplateDefinition("hello",
                List.of("Hello ${name}"), Map.of()));
        newTemplates.put("goodbye", new TemplateDefinition("goodbye",
                List.of("Goodbye ${name}"), Map.of()));
        newTemplates.put("thanks", new TemplateDefinition("thanks",
                List.of("Thank ${name}"), Map.of()));
        registry.replaceOrRegister(new TemplateNamespace("test-ns", newTemplates, "en"));

        TemplateMatch oldResult = registry.match("test-ns", "Cancel my order");
        assertThat(oldResult).isNotInstanceOf(TemplateMatch.Matched.class);

        TemplateMatch newResult = registry.match("test-ns", "Hello world");
        assertThat(newResult).isInstanceOf(TemplateMatch.Matched.class);
    }

    private TemplateNamespace buildNamespace(String name, int count) {
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        if (count >= 1) templates.put("cancel", new TemplateDefinition("cancel",
                List.of("Cancel ${item}"), Map.of()));
        if (count >= 2) templates.put("submit", new TemplateDefinition("submit",
                List.of("Submit ${item}"), Map.of()));
        if (count >= 3) templates.put("review", new TemplateDefinition("review",
                List.of("Review ${item}"), Map.of()));
        if (count >= 4) templates.put("approve", new TemplateDefinition("approve",
                List.of("Approve ${item}"), Map.of()));
        if (count >= 5) templates.put("reject", new TemplateDefinition("reject",
                List.of("Reject ${item}"), Map.of()));
        return new TemplateNamespace(name, templates, "en");
    }
}
