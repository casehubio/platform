package io.casehub.template.registry.llm;

import io.casehub.template.registry.AdaptiveFallbackMatcher;
import io.casehub.template.registry.ClassificationResult;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.VariationSeeder;
import io.casehub.template.registry.nlp.OpenNlpTokenizer;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveRegistryIntegrationTest {

    private OpenNlpTokenizer tokenizer;

    @BeforeEach
    void setUp() {
        tokenizer = new OpenNlpTokenizer();
    }

    private TemplateNamespace reviewNamespace() {
        return new TemplateNamespace("protocol:review", Map.of("vote",
            TemplateDefinition.builder("vote")
                .pattern("I ${decision} this ${subject}")
                .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                .variable("subject", VariableConstraint.string())
                .build()));
    }

    private ClassificationCache newCache() {
        return new ClassificationCache(Duration.ofMinutes(30), Duration.ofMinutes(5), 1000);
    }

    @Nested
    class SeededVariations {

        @Test
        void structuralRearrangement_matchesWithoutLlm() {
            VariationSeeder seeder = (def, lang) ->
                List.of("This ${subject} gets my ${decision}");

            var registry = new AdaptiveTemplateRegistry(tokenizer,
                (input, candidates, lang) -> { throw new RuntimeException("Should not be called"); },
                0.8, newCache());

            var service = new VariationSeedingService(seeder, registry);
            service.seedAndRegister(reviewNamespace());

            var result = registry.match("protocol:review", "This architecture gets my approve");
            assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
            var matched = (TemplateMatch.Matched) result;
            assertThat(matched.score()).isEqualTo(1.0);
            assertThat(matched.fields()).containsEntry("decision", "approve");
            assertThat(matched.fields()).containsEntry("subject", "architecture");
        }

        @Test
        void seededVariation_doesNotBridgeSemanticParaphrase() {
            VariationSeeder seeder = (def, lang) ->
                List.of("This ${subject} gets my ${decision}");

            var registry = new AdaptiveTemplateRegistry(tokenizer,
                (input, candidates, lang) -> new ClassificationResult.Unmatched(),
                0.8, newCache());

            var service = new VariationSeedingService(seeder, registry);
            service.seedAndRegister(reviewNamespace());

            var structural = registry.matchStructural("I'm in favour of this design");
            assertThat(structural).isInstanceOf(TemplateMatch.Unmatched.class);
        }
    }

    @Nested
    class AdaptiveFallbackBridgesParaphraseCliff {

        @Test
        void semanticParaphrase_matchesViaAdaptiveFallback() {
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) ->
                new ClassificationResult.Matched("vote",
                    Map.of("decision", "approve", "subject", "design"),
                    "I'm in favour of this ${subject}", 0.95);

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            var result = registry.match("protocol:review", "I'm in favour of this design");
            assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
            var matched = (TemplateMatch.Matched) result;
            assertThat(matched.templateName()).isEqualTo("vote");
            assertThat(matched.fields()).containsEntry("decision", "approve");
            assertThat(matched.fields()).containsEntry("subject", "design");
            assertThat(matched.score()).isEqualTo(0.9);
        }

        @Test
        void classificationCachePreventsRepeatedLlmCalls() {
            var counter = new AtomicInteger();
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) -> {
                counter.incrementAndGet();
                return new ClassificationResult.Matched("vote",
                    Map.of("decision", "approve", "subject", "design"),
                    "I'm in favour of this ${subject}", 0.95);
            };

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            registry.match("protocol:review", "I'm in favour of this design");
            registry.match("protocol:review", "I'm in favour of this design");
            registry.match("protocol:review", "I'm in favour of this design");

            assertThat(counter.get()).isEqualTo(1);
        }
    }

    @Nested
    class RuntimeLearnedVariations {

        @Test
        void learnedVariation_matchesStructurallyOnSecondOccurrence() {
            var counter = new AtomicInteger();
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) -> {
                counter.incrementAndGet();
                return new ClassificationResult.Matched("vote",
                    Map.of("decision", "approve", "subject", "design"),
                    "This ${subject} gets my ${decision}", 0.95);
            };

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            var first = registry.match("protocol:review", "This design gets my approve");
            assertThat(first).isInstanceOf(TemplateMatch.Matched.class);
            assertThat(counter.get()).isEqualTo(1);

            var second = registry.match("protocol:review", "This architecture gets my reject");
            assertThat(second).isInstanceOf(TemplateMatch.Matched.class);
            var matched = (TemplateMatch.Matched) second;
            assertThat(matched.score()).isEqualTo(1.0);
        }
    }

    @Nested
    class CollisionGuard {

        @Test
        void adaptiveFallback_rejectsCollidingVariation() {
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) ->
                new ClassificationResult.Matched("vote",
                    Map.of("decision", "approve", "subject", "design"),
                    "I ${decision} this ${subject}", 0.95);

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            var ns2 = new TemplateNamespace("protocol:other", Map.of("action",
                TemplateDefinition.builder("action")
                    .pattern("${subject} receives ${decision}")
                    .variable("decision", VariableConstraint.string())
                    .variable("subject", VariableConstraint.string())
                    .build()));
            registry.register(ns2);

            registry.match("protocol:other", "some unmatched input");

            assertThat(registry.schemas("protocol:other").get(0).patterns()).hasSize(1);
        }

        @Test
        void seedingService_rejectsCollidingVariation() {
            var registry = new AdaptiveTemplateRegistry(tokenizer,
                (i, c, l) -> new ClassificationResult.Unmatched(), 0.8, newCache());

            var ns1 = new TemplateNamespace("ns1", Map.of("t1",
                TemplateDefinition.builder("t1")
                    .pattern("I ${action} this ${thing}")
                    .variable("action", VariableConstraint.string())
                    .variable("thing", VariableConstraint.string())
                    .build()));
            registry.register(ns1);

            VariationSeeder seeder = (def, lang) ->
                List.of("I ${action} this ${thing}");

            var service = new VariationSeedingService(seeder, registry);
            var ns2 = new TemplateNamespace("ns2", Map.of("t2",
                TemplateDefinition.builder("t2")
                    .pattern("${thing} receives my ${action}")
                    .variable("action", VariableConstraint.string())
                    .variable("thing", VariableConstraint.string())
                    .build()));

            service.seedAndRegister(ns2);
            assertThat(registry.schemas("ns2").get(0).patterns()).hasSize(1);
        }
    }

    @Nested
    class ConfidenceThreshold {

        @Test
        void lowConfidence_preventsVariationRegistration() {
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) ->
                new ClassificationResult.Matched("vote",
                    Map.of("decision", "approve", "subject", "design"),
                    "This ${subject} gets my ${decision}", 0.5);

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            var result = registry.match("protocol:review", "This design gets my approve");
            assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
            assertThat(registry.schemas("protocol:review").get(0).patterns()).hasSize(1);
        }
    }
}
