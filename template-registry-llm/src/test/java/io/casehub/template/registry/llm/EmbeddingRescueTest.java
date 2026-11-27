package io.casehub.template.registry.llm;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.casehub.template.registry.ClassificationResult;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingRescueTest {

    private final NlpTokenizerFactory tokenizerFactory = new OpenNlpTokenizerFactory();
    private final LanguageFamilyRegistry familyRegistry = createFamilyRegistry();
    private final Map<String, MatchingStrategy> strategies = Map.of("svo", new SvoMatchingStrategy());

    private static LanguageFamilyRegistry createFamilyRegistry() {
        var r = new LanguageFamilyRegistry();
        r.register("en", "svo");
        return r;
    }

    @Test
    void trailingKeywordGap_embeddingRescuesPartialMatch() {
        var embeddingModel = stubEmbeddingModel(0.75f);
        var registry       = createRegistry(embeddingModel, 0.5);
        registerTrailingKeywordTemplate(registry);

        TemplateMatch result = registry.match("test-ns", "Handle the report at headquarters");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        var matched = (TemplateMatch.Matched) result;
        assertThat(matched.templateName()).isEqualTo("action-template");
        assertThat(matched.fields()).containsEntry("action", "Handle");
    }

    @Test
    void noEmbeddingModel_keywordFilteredFallsToLlm() {
        var registry = createRegistry(null, 0.5);
        registerTrailingKeywordTemplate(registry);

        TemplateMatch result = registry.match("test-ns", "Handle the report at headquarters");
        assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
    }

    @Test
    void belowThreshold_embeddingDoesNotRescue() {
        var embeddingModel = stubEmbeddingModel(0.3f);
        var registry       = createRegistry(embeddingModel, 0.5);
        registerTrailingKeywordTemplate(registry);

        TemplateMatch result = registry.match("test-ns", "The weather is nice today");
        assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
    }

    @Test
    void keywordAffinityPasses_embeddingNotCalled() {
        var callCount = new AtomicInteger(0);
        EmbeddingModel countingModel = new EmbeddingModel() {
            @Override
            public Response<java.util.List<Embedding>> embedAll(java.util.List<TextSegment> segments) {
                callCount.addAndGet(segments.size());
                return Response.from(segments.stream()
                                             .map(s -> Embedding.from(new float[]{1.0f}))
                                             .toList());
            }
        };
        var registry = createRegistry(countingModel, 0.5);

        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("cancel", new TemplateDefinition("cancel",
                                                       List.of("Cancel ${item}"), Map.of()));
        registry.register(new TemplateNamespace("test-ns", templates, "en"));

        int callsAfterRegister = callCount.get();
        registry.match("test-ns", "Cancel my subscription");
        assertThat(callCount.get()).isEqualTo(callsAfterRegister);
    }

    @Test
    void embeddingModelThrows_degradesGracefully() {
        EmbeddingModel failingModel = new EmbeddingModel() {
            @Override
            public Response<java.util.List<Embedding>> embedAll(java.util.List<TextSegment> segments) {
                throw new RuntimeException("API down");
            }
        };
        var registry = createRegistry(failingModel, 0.5);
        registerTrailingKeywordTemplate(registry);

        TemplateMatch result = registry.match("test-ns", "Handle the report at headquarters");
        assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
    }

    @Test
    void replaceOrRegister_clearsEmbeddingsAndSynonyms() {
        var embeddingModel = stubEmbeddingModel(0.75f);
        var registry       = createRegistry(embeddingModel, 0.5);
        registerTrailingKeywordTemplate(registry);

        // First match — embedding rescue should work
        TemplateMatch first = registry.match("test-ns", "Handle the report at headquarters");
        assertThat(first).isInstanceOf(TemplateMatch.Matched.class);

        // Replace namespace with a different template that won't match
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("other", new TemplateDefinition("other",
                                                      List.of("Completely different ${thing}"), Map.of()));
        registry.replaceOrRegister(new TemplateNamespace("test-ns", templates, "en"));

        // Old embedding data should be cleared — this input shouldn't match the new template
        TemplateMatch second = registry.match("test-ns", "Handle the report at headquarters");
        assertThat(second).isInstanceOf(TemplateMatch.Unmatched.class);
    }

    private AdaptiveTemplateRegistry createRegistry(EmbeddingModel embeddingModel,
                                                    double embeddingSimilarityThreshold) {
        return new AdaptiveTemplateRegistry(tokenizerFactory, familyRegistry, strategies, 0.5,
                                            (input, candidates, language) -> new ClassificationResult.Unmatched(),
                                            0.8, new ClassificationCache(Duration.ofMinutes(5), Duration.ofMinutes(1), 100),
                                            embeddingModel, embeddingSimilarityThreshold);
    }

    private void registerTrailingKeywordTemplate(AdaptiveTemplateRegistry registry) {
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("action-template", new TemplateDefinition("action-template",
                                                                List.of("${action} the ${target} at ${location} promptly"), Map.of()));
        registry.register(new TemplateNamespace("test-ns", templates, "en"));
    }

    private static EmbeddingModel stubEmbeddingModel(float similarity) {
        float   bx          = similarity;
        float   by          = (float) Math.sqrt(1.0 - similarity * similarity);
        float[] templateVec = new float[]{1.0f, 0.0f};
        float[] inputVec    = new float[]{bx, by};
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
