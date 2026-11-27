package io.casehub.template.registry.llm;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.casehub.template.registry.CandidateOutcome;
import io.casehub.template.registry.ClassificationResult;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.LanguageFamilyRegistry;
import io.casehub.template.registry.MatchingStrategy;
import io.casehub.template.registry.NlpTokenizerFactory;
import io.casehub.template.registry.nlp.svo.OpenNlpTokenizerFactory;
import io.casehub.template.registry.nlp.svo.SvoMatchingStrategy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingDiagnosticsTest {

    private final NlpTokenizerFactory tokenizerFactory = new OpenNlpTokenizerFactory();
    private final LanguageFamilyRegistry familyRegistry = createFamilyRegistry();
    private final Map<String, MatchingStrategy> strategies = Map.of("svo", new SvoMatchingStrategy());

    private static LanguageFamilyRegistry createFamilyRegistry() {
        var r = new LanguageFamilyRegistry();
        r.register("en", "svo");
        return r;
    }

    @Test
    void matchWithTrace_includesEmbeddingRescuedOutcome() {
        var embeddingModel = stubEmbeddingModel(0.75f);
        var registry = createRegistry(embeddingModel);
        registerTrailingKeywordTemplate(registry);

        var result = registry.matchWithTrace("test-ns", "Handle the report at headquarters");
        assertThat(result.result()).isInstanceOf(io.casehub.template.registry.TemplateMatch.Matched.class);

        var rescued = result.trace().candidates().stream()
                .filter(c -> c.outcome() == CandidateOutcome.EMBEDDING_RESCUED)
                .findFirst();
        assertThat(rescued).isPresent();
        assertThat(rescued.get().embeddingSimilarity()).isGreaterThan(0.5);
    }

    @Test
    void matchWithTrace_includesEmbeddingFilteredOutcome() {
        var embeddingModel = stubEmbeddingModel(0.3f);
        var registry = createRegistry(embeddingModel);
        registerTrailingKeywordTemplate(registry);

        var result = registry.matchWithTrace("test-ns", "The weather is nice today");

        var filtered = result.trace().candidates().stream()
                .filter(c -> c.outcome() == CandidateOutcome.EMBEDDING_FILTERED)
                .findFirst();
        assertThat(filtered).isPresent();
        assertThat(filtered.get().embeddingSimilarity()).isLessThan(0.5);
    }

    @Test
    void matchWithTrace_keywordFiltered_showsNegativeEmbeddingSimilarity() {
        // No embedding model — keyword filtered patterns have -1.0 embedding similarity
        var registry = createRegistry(null);
        registerTrailingKeywordTemplate(registry);

        var result = registry.matchWithTrace("test-ns", "Handle the report at headquarters");

        var keywordFiltered = result.trace().candidates().stream()
                .filter(c -> c.outcome() == CandidateOutcome.KEYWORD_FILTERED)
                .findFirst();
        assertThat(keywordFiltered).isPresent();
        assertThat(keywordFiltered.get().embeddingSimilarity()).isEqualTo(-1.0);
    }

    private AdaptiveTemplateRegistry createRegistry(EmbeddingModel embeddingModel) {
        return new AdaptiveTemplateRegistry(tokenizerFactory, familyRegistry, strategies, 0.5,
                (input, candidates, language) -> new ClassificationResult.Unmatched(),
                0.8, new ClassificationCache(Duration.ofMinutes(5), Duration.ofMinutes(1), 100),
                embeddingModel, 0.5);
    }

    private void registerTrailingKeywordTemplate(AdaptiveTemplateRegistry registry) {
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("action-template", new TemplateDefinition("action-template",
                List.of("${action} the ${target} at ${location} promptly"), Map.of()));
        registry.register(new TemplateNamespace("test-ns", templates, "en"));
    }

    private static EmbeddingModel stubEmbeddingModel(float similarity) {
        float bx = similarity;
        float by = (float) Math.sqrt(1.0 - similarity * similarity);
        float[] templateVec = new float[]{1.0f, 0.0f};
        float[] inputVec = new float[]{bx, by};
        return new EmbeddingModel() {
            @Override
            public Response<java.util.List<Embedding>> embedAll(java.util.List<TextSegment> segments) {
                return Response.from(segments.stream()
                        .map(s -> {
                            boolean isTemplateKeyword = s.text().toLowerCase().contains("promptly");
                            return Embedding.from(isTemplateKeyword ? templateVec : inputVec);
                        })
                        .toList());
            }
        };
    }
}
