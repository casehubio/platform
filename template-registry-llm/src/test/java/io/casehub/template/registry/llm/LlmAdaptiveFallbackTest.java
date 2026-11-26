package io.casehub.template.registry.llm;

import io.casehub.template.registry.ClassificationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmAdaptiveFallbackTest {

    private final LlmAdaptiveFallback fallback = new LlmAdaptiveFallback(null);

    @Test
    void parseResult_matchedResponse() {
        var result = fallback.parseResult(
            "{\"match\": true, \"templateName\": \"vote\", \"fields\": {\"decision\": \"approve\", \"subject\": \"design\"}, \"suggestedVariation\": \"I'm in favour of this ${subject}\", \"confidence\": 0.95}");

        assertThat(result).isInstanceOf(ClassificationResult.Matched.class);
        var matched = (ClassificationResult.Matched) result;
        assertThat(matched.templateName()).isEqualTo("vote");
        assertThat(matched.fields()).containsEntry("decision", "approve");
        assertThat(matched.fields()).containsEntry("subject", "design");
        assertThat(matched.suggestedVariation()).isEqualTo("I'm in favour of this ${subject}");
        assertThat(matched.confidence()).isEqualTo(0.95);
    }

    @Test
    void parseResult_unmatchedResponse() {
        var result = fallback.parseResult("{\"match\": false}");
        assertThat(result).isInstanceOf(ClassificationResult.Unmatched.class);
    }

    @Test
    void parseResult_codeFencedResponse() {
        var result = fallback.parseResult("```json\n{\"match\": true, \"templateName\": \"vote\", \"fields\": {}, \"suggestedVariation\": \"pattern\", \"confidence\": 0.8}\n```");
        assertThat(result).isInstanceOf(ClassificationResult.Matched.class);
    }

    @Test
    void parseResult_invalidJson() {
        var result = fallback.parseResult("not json");
        assertThat(result).isInstanceOf(ClassificationResult.Unmatched.class);
    }

    @Test
    void parseResult_missingTemplateName() {
        var result = fallback.parseResult("{\"match\": true, \"fields\": {}, \"suggestedVariation\": \"p\", \"confidence\": 0.9}");
        assertThat(result).isInstanceOf(ClassificationResult.Unmatched.class);
    }

    @Test
    void parseResult_defaultConfidence() {
        var result = fallback.parseResult("{\"match\": true, \"templateName\": \"t\", \"fields\": {}, \"suggestedVariation\": \"p\"}");
        assertThat(result).isInstanceOf(ClassificationResult.Matched.class);
        assertThat(((ClassificationResult.Matched) result).confidence()).isEqualTo(0.5);
    }
}
