package io.casehub.template.registry.llm;

import io.casehub.template.registry.ClassificationResult;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.nlp.OpenNlpTokenizer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrieCompatibilityTest {

    private final OpenNlpTokenizer tokenizer = new OpenNlpTokenizer();

    @Test
    void adaptiveRegistry_inheritsTrieBehavior() {
        var registry = new AdaptiveTemplateRegistry(tokenizer,
                (input, candidates, language) -> new ClassificationResult.Unmatched(),
                0.8, new ClassificationCache(Duration.ofMinutes(5), Duration.ofMinutes(1), 100));

        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("cancel", new TemplateDefinition("cancel",
                List.of("Cancel ${item}"), Map.of()));
        templates.put("submit", new TemplateDefinition("submit",
                List.of("Submit ${item}"), Map.of()));
        registry.register(new TemplateNamespace("test-ns", templates, "en"));

        TemplateMatch result = registry.match("test-ns", "Cancel my order");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(((TemplateMatch.Matched) result).templateName()).isEqualTo("cancel");
    }

    @Test
    void adaptiveRegistry_llmFallbackStillWorks() {
        var registry = new AdaptiveTemplateRegistry(tokenizer,
                (input, candidates, language) -> new ClassificationResult.Matched(
                        "cancel", Map.of("item", "subscription"),
                        "Cancel my ${item}", 0.95),
                0.8, new ClassificationCache(Duration.ofMinutes(5), Duration.ofMinutes(1), 100));

        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("cancel", new TemplateDefinition("cancel",
                List.of("Cancel ${item}"), Map.of()));
        registry.register(new TemplateNamespace("test-ns", templates, "en"));

        TemplateMatch result = registry.match("test-ns", "I want to stop my subscription");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
    }
}
