package io.casehub.template.registry.llm;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
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
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SynonymLearningTest {

    private final OpenNlpTokenizer tokenizer = new OpenNlpTokenizer();
    private static final double SYNONYM_LEARNING_THRESHOLD = 0.7;

    @Test
    void embeddingRescue_learnsSynonymForMissingKeyword() {
        // Template "${action} the ${target} at ${location} promptly" — keyword "promptly"
        // Input "Handle the report at headquarters" — missing "promptly"
        // After embedding rescue + structural match, "promptly" should learn a synonym
        var embeddingModel = synonymAwareEmbeddingModel();
        var registry = createRegistry(embeddingModel);
        registerTrailingKeywordTemplate(registry);

        // First match — embedding rescue should work and learn synonym
        TemplateMatch first = registry.match("test-ns", "Handle the report at headquarters");
        assertThat(first).isInstanceOf(TemplateMatch.Matched.class);

        // Verify synonym was learned — the namespace synonyms map should not be empty
        ConcurrentMap<String, ConcurrentMap<String, Set<String>>> syns = registry.synonyms();
        assertThat(syns.get("test-ns")).isNotNull();
        assertThat(syns.get("test-ns")).isNotEmpty();
    }

    @Test
    void synonymClearedOnNamespaceReplace() {
        var embeddingModel = synonymAwareEmbeddingModel();
        var registry = createRegistry(embeddingModel);
        registerTrailingKeywordTemplate(registry);

        // Learn synonym
        registry.match("test-ns", "Handle the report at headquarters");
        assertThat(registry.synonyms().get("test-ns")).isNotNull();

        // Replace namespace — synonyms should be cleared
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("other", new TemplateDefinition("other",
                List.of("Completely different ${thing}"), Map.of()));
        registry.replaceOrRegister(new TemplateNamespace("test-ns", templates, "en"));

        assertThat(registry.synonyms().get("test-ns")).isNull();
    }

    @Test
    void concurrentSynonymLearning_noCorruption() throws Exception {
        var embeddingModel = synonymAwareEmbeddingModel();
        var registry = createRegistry(embeddingModel);
        registerTrailingKeywordTemplate(registry);

        var threads = new Thread[10];
        var errors = new AtomicInteger(0);
        for (int i = 0; i < threads.length; i++) {
            threads[i] = Thread.ofVirtual().start(() -> {
                try {
                    registry.match("test-ns", "Handle the report at headquarters");
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }
        for (Thread t : threads) t.join();
        assertThat(errors.get()).isEqualTo(0);
    }

    @Test
    void secondMatchUsesSynonymAffinity() {
        var callCount = new AtomicInteger(0);
        var embeddingModel = countingEmbeddingModel(callCount);
        var registry = createRegistry(embeddingModel);
        registerTrailingKeywordTemplate(registry);

        // First match — embedding rescue + synonym learning
        int callsBeforeFirst = callCount.get();
        registry.match("test-ns", "Handle the report at headquarters");
        int embeddingCallsDuringFirst = callCount.get() - callsBeforeFirst;
        assertThat(embeddingCallsDuringFirst).isGreaterThan(0);

        // Second match with same input — should use synonym-expanded affinity
        int callsBeforeSecond = callCount.get();
        TemplateMatch second = registry.match("test-ns", "Handle the report at headquarters");
        int embeddingCallsDuringSecond = callCount.get() - callsBeforeSecond;

        // If synonym learning worked, keyword affinity passes via synonym expansion
        // and embedding model is not called (or called fewer times)
        assertThat(second).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(embeddingCallsDuringSecond).isEqualTo(0);
    }

    private AdaptiveTemplateRegistry createRegistry(EmbeddingModel embeddingModel) {
        return new AdaptiveTemplateRegistry(tokenizer,
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

    private static EmbeddingModel synonymAwareEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public Response<java.util.List<Embedding>> embedAll(java.util.List<TextSegment> segments) {
                return Response.from(segments.stream()
                        .map(s -> {
                            // Return similar vectors for all words — simulating semantic similarity
                            float[] vec = new float[]{0.9f, 0.1f};
                            return Embedding.from(vec);
                        })
                        .toList());
            }
        };
    }

    private static EmbeddingModel countingEmbeddingModel(AtomicInteger counter) {
        float bx = 0.75f;
        float by = (float) Math.sqrt(1.0 - bx * bx);
        float[] templateVec = new float[]{1.0f, 0.0f};
        float[] inputVec = new float[]{bx, by};
        return new EmbeddingModel() {
            @Override
            public Response<java.util.List<Embedding>> embedAll(java.util.List<TextSegment> segments) {
                counter.addAndGet(segments.size());
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
