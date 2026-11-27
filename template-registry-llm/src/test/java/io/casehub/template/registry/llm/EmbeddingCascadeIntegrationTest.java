package io.casehub.template.registry.llm;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.casehub.template.registry.CandidateOutcome;
import io.casehub.template.registry.ClassificationResult;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.nlp.OpenNlpTokenizer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingCascadeIntegrationTest {

    private final OpenNlpTokenizer tokenizer = new OpenNlpTokenizer();

    @Nested
    class TrailingKeywordRescueProof {
        @Test
        void trailingKeyword_embeddingRescuesPartialMatch() {
            var registry = createRegistryWithEmbedding(0.75f);
            registerTrailingKeywordTemplate(registry);

            TemplateMatch result = registry.match("test-ns", "Handle the report at headquarters");
            assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
            var matched = (TemplateMatch.Matched) result;
            assertThat(matched.templateName()).isEqualTo("action-template");
            assertThat(matched.score()).isGreaterThan(0.8);
        }
    }

    @Nested
    class LearningAccelerationProof {
        @Test
        void secondMatch_usesSynonyms_skipEmbedding() {
            var callCount = new AtomicInteger(0);
            var registry = createRegistryWithCountingModel(callCount);
            registerTrailingKeywordTemplate(registry);

            int callsAfterRegister = callCount.get();
            registry.match("test-ns", "Handle the report at headquarters");
            int embeddingCallsDuringFirst = callCount.get() - callsAfterRegister;
            assertThat(embeddingCallsDuringFirst).isGreaterThan(0);

            int callsBeforeSecond = callCount.get();
            TemplateMatch second = registry.match("test-ns", "Handle the report at headquarters");
            int embeddingCallsDuringSecond = callCount.get() - callsBeforeSecond;

            assertThat(second).isInstanceOf(TemplateMatch.Matched.class);
            assertThat(embeddingCallsDuringSecond).isEqualTo(0);
        }
    }

    @Nested
    class ThresholdBoundary {
        @Test
        void atThreshold_rescuePasses() {
            var registry = createRegistryWithEmbedding(0.5f);
            registerTrailingKeywordTemplate(registry);

            TemplateMatch result = registry.match("test-ns", "Handle the report at headquarters");
            assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        }

        @Test
        void justBelowThreshold_rescueFails() {
            var registry = createRegistryWithEmbedding(0.49f);
            registerTrailingKeywordTemplate(registry);

            TemplateMatch result = registry.match("test-ns", "Handle the report at headquarters");
            assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
        }
    }

    @Nested
    class EmbeddingModelFailures {
        @Test
        void throwsDuringMatch_degradesGracefully() {
            EmbeddingModel failingModel = new EmbeddingModel() {
                private boolean firstCall = true;

                @Override
                public Response<java.util.List<Embedding>> embedAll(java.util.List<TextSegment> segments) {
                    if (firstCall) {
                        firstCall = false;
                        return Response.from(segments.stream()
                                .map(s -> Embedding.from(new float[]{1.0f, 0.0f}))
                                .toList());
                    }
                    throw new RuntimeException("Transient API failure");
                }
            };
            var registry = new AdaptiveTemplateRegistry(tokenizer,
                    (input, candidates, language) -> new ClassificationResult.Unmatched(),
                    0.8, newCache(), failingModel, 0.5);
            registerTrailingKeywordTemplate(registry);

            TemplateMatch result = registry.match("test-ns", "Handle the report at headquarters");
            assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
        }

        @Test
        void nullEmbeddingModel_standardBehavior() {
            var registry = new AdaptiveTemplateRegistry(tokenizer,
                    (input, candidates, language) -> new ClassificationResult.Unmatched(),
                    0.8, newCache());
            registerTrailingKeywordTemplate(registry);

            TemplateMatch result = registry.match("test-ns", "Handle the report at headquarters");
            assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
        }
    }

    @Nested
    class NamespaceReplaceMidFlight {
        @Test
        void replaceClears_synonymsAndEmbeddings() {
            var registry = createRegistryWithEmbedding(0.75f);
            registerTrailingKeywordTemplate(registry);

            registry.match("test-ns", "Handle the report at headquarters");
            assertThat(registry.synonyms().get("test-ns")).isNotNull();

            var templates = new LinkedHashMap<String, TemplateDefinition>();
            templates.put("other", new TemplateDefinition("other",
                    List.of("Completely different ${thing}"), Map.of()));
            registry.replaceOrRegister(new TemplateNamespace("test-ns", templates, "en"));

            assertThat(registry.synonyms().get("test-ns")).isNull();
        }
    }

    @Nested
    class MatchStructuralBypass {
        @Test
        void matchStructural_doesNotCallLlmFallback() {
            var callCount = new AtomicInteger(0);
            var registry = new AdaptiveTemplateRegistry(tokenizer,
                                                        (input, candidates, language) -> {
                                                            callCount.incrementAndGet();
                                                            return new ClassificationResult.Unmatched();
                                                        },
                                                        0.8, newCache(), stubEmbeddingModel(0.75f), 0.5);
            registerTrailingKeywordTemplate(registry);

            registry.matchStructural("Handle the report at headquarters");
            assertThat(callCount.get()).isEqualTo(0);
        }
    }

    @Nested
    class EmptyKeywordSet {
        @Test
        void allVariableTemplate_bypassesKeywordFilter() {
            var registry = createRegistryWithEmbedding(0.75f);

            var templates = new LinkedHashMap<String, TemplateDefinition>();
            templates.put("greeting", new TemplateDefinition("greeting",
                    List.of("${greeting} the ${target}"), Map.of()));
            registry.register(new TemplateNamespace("test-ns", templates, "en"));

            TemplateMatch result = registry.match("test-ns", "Hello the world");
            assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        }
    }

    @Nested
    class DiagnosticsIntegration {
        @Test
        void traceShowsEmbeddingOutcomes() {
            var registry = createRegistryWithEmbedding(0.75f);
            registerTrailingKeywordTemplate(registry);

            var result = registry.matchWithTrace("test-ns", "Handle the report at headquarters");

            boolean hasEmbeddingOutcome = result.trace().candidates().stream()
                    .anyMatch(c -> c.outcome() == CandidateOutcome.EMBEDDING_RESCUED
                            || c.outcome() == CandidateOutcome.EMBEDDING_FILTERED);
            assertThat(hasEmbeddingOutcome).isTrue();
        }
    }

    private AdaptiveTemplateRegistry createRegistryWithEmbedding(float similarity) {
        return new AdaptiveTemplateRegistry(tokenizer,
                (input, candidates, language) -> new ClassificationResult.Unmatched(),
                0.8, newCache(), stubEmbeddingModel(similarity), 0.5);
    }

    private AdaptiveTemplateRegistry createRegistryWithCountingModel(AtomicInteger counter) {
        float bx = 0.75f;
        float by = (float) Math.sqrt(1.0 - bx * bx);
        float[] templateVec = new float[]{1.0f, 0.0f};
        float[] inputVec = new float[]{bx, by};
        EmbeddingModel model = new EmbeddingModel() {
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
        return new AdaptiveTemplateRegistry(tokenizer,
                (input, candidates, language) -> new ClassificationResult.Unmatched(),
                0.8, newCache(), model, 0.5);
    }

    private ClassificationCache newCache() {
        return new ClassificationCache(Duration.ofMinutes(5), Duration.ofMinutes(1), 100);
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
