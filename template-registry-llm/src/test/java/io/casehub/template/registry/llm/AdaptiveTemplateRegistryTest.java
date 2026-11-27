package io.casehub.template.registry.llm;

import io.casehub.template.registry.AdaptiveFallbackMatcher;
import io.casehub.template.registry.CandidateOutcome;
import io.casehub.template.registry.ClassificationResult;
import io.casehub.template.registry.LanguageFamilyRegistry;
import io.casehub.template.registry.MatchingStrategy;
import io.casehub.template.registry.NlpTokenizerFactory;
import io.casehub.template.registry.TemplateCandidate;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.nlp.svo.OpenNlpTokenizerFactory;
import io.casehub.template.registry.nlp.svo.SvoMatchingStrategy;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveTemplateRegistryTest {

    private NlpTokenizerFactory tokenizerFactory;
    private LanguageFamilyRegistry familyRegistry;
    private Map<String, MatchingStrategy> strategies;
    private TemplateNamespace reviewNamespace;

    @BeforeEach
    void setUp() {
        tokenizerFactory = new OpenNlpTokenizerFactory();
        familyRegistry = new LanguageFamilyRegistry();
        familyRegistry.register("en", "svo");
        strategies = Map.of("svo", new SvoMatchingStrategy());
        reviewNamespace = new TemplateNamespace("protocol:review", Map.of("vote",
            TemplateDefinition.builder("vote")
                .pattern("I ${decision} this ${subject}")
                .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                .variable("subject", VariableConstraint.string())
                .build()));
    }

    private ClassificationCache newCache() {
        return new ClassificationCache(
            Duration.ofMinutes(30), Duration.ofMinutes(5), 1000);
    }

    @Test
    void match_delegatesToAdaptiveFallbackOnUnmatched() {
        var fallback = stubFallback(new ClassificationResult.Matched(
            "vote", Map.of("decision", "approve", "subject", "design"),
            "I'm in favour of this ${subject}", 0.95));

        var registry = new AdaptiveTemplateRegistry(
            tokenizerFactory, familyRegistry, strategies, 0.5, fallback, 0.8, newCache());
        registry.register(reviewNamespace);

        var result = registry.match("protocol:review",
            "I'm in favour of this design");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        var matched = (TemplateMatch.Matched) result;
        assertThat(matched.templateName()).isEqualTo("vote");
        assertThat(matched.fields()).containsEntry("decision", "approve");
        assertThat(matched.score()).isEqualTo(0.9);
        assertThat(matched.patternIndex()).isEqualTo(-1);
    }

    @Test
    void match_regexTierBypassesAdaptive() {
        var counter = new AtomicInteger();
        var fallback = countingFallback(counter, new ClassificationResult.Unmatched());

        var registry = new AdaptiveTemplateRegistry(
            tokenizerFactory, familyRegistry, strategies, 0.5, fallback, 0.8, newCache());
        registry.register(reviewNamespace);

        var result = registry.match("protocol:review", "I approve this design");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(((TemplateMatch.Matched) result).score()).isEqualTo(1.0);
        assertThat(counter.get()).isEqualTo(0);
    }

    @Test
    void match_degradesToUnmatchedOnLlmFailure() {
        AdaptiveFallbackMatcher failingFallback =
            (input, candidates, lang) -> { throw new RuntimeException("LLM down"); };

        var registry = new AdaptiveTemplateRegistry(
            tokenizerFactory, familyRegistry, strategies, 0.5, failingFallback, 0.8, newCache());
        registry.register(reviewNamespace);

        var result = registry.match("protocol:review",
            "I'm in favour of this design");
        assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
    }

    @Test
    void match_usesCacheOnSecondCall() {
        var counter = new AtomicInteger();
        var fallback = countingFallback(counter, new ClassificationResult.Matched(
            "vote", Map.of("decision", "approve", "subject", "design"),
            "I'm in favour of this ${subject}", 0.95));

        var registry = new AdaptiveTemplateRegistry(
            tokenizerFactory, familyRegistry, strategies, 0.5, fallback, 0.8, newCache());
        registry.register(reviewNamespace);

        registry.match("protocol:review", "I'm in favour of this design");
        registry.match("protocol:review", "I'm in favour of this design");

        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void match_cachesNegativeResult() {
        var counter = new AtomicInteger();
        var fallback = countingFallback(counter, new ClassificationResult.Unmatched());

        var registry = new AdaptiveTemplateRegistry(
            tokenizerFactory, familyRegistry, strategies, 0.5, fallback, 0.8, newCache());
        registry.register(reviewNamespace);

        registry.match("protocol:review", "completely unrelated");
        registry.match("protocol:review", "completely unrelated");

        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void learnedVariation_matchesStructurallyWithoutLlm() {
        var fallback = stubFallback(new ClassificationResult.Matched(
            "vote", Map.of("decision", "approve", "subject", "design"),
            "This ${subject} gets my ${decision}", 0.95));

        var registry = new AdaptiveTemplateRegistry(
            tokenizerFactory, familyRegistry, strategies, 0.5, fallback, 0.8, newCache());
        registry.register(reviewNamespace);

        registry.match("protocol:review", "This design gets my approve");

        AdaptiveFallbackMatcher failingFallback =
            (input, candidates, lang) -> { throw new RuntimeException("Should not be called"); };
        var registry2 = new AdaptiveTemplateRegistry(
            tokenizerFactory, familyRegistry, strategies, 0.5, failingFallback, 0.8, newCache());
        registry2.register(reviewNamespace);

        var schemas = registry.schemas("protocol:review");
        assertThat(schemas.get(0).patterns()).contains("This ${subject} gets my ${decision}");

        var result = registry.match("protocol:review", "This architecture gets my reject");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(((TemplateMatch.Matched) result).score()).isEqualTo(1.0);
    }

    @Test
    void lowConfidence_returnsMatchButDoesNotRegisterVariation() {
        var fallback = stubFallback(new ClassificationResult.Matched(
            "vote", Map.of("decision", "approve", "subject", "design"),
            "This ${subject} gets my ${decision}", 0.5));

        var registry = new AdaptiveTemplateRegistry(
            tokenizerFactory, familyRegistry, strategies, 0.5, fallback, 0.8, newCache());
        registry.register(reviewNamespace);

        var result = registry.match("protocol:review",
            "This design gets my approve");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);

        var schemas = registry.schemas("protocol:review");
        assertThat(schemas.get(0).patterns()).hasSize(1);
    }

    @Test
    void variationThatDropsVariable_notRegistered() {
        var fallback = stubFallback(new ClassificationResult.Matched(
            "vote", Map.of("decision", "approve", "subject", "design"),
            "I'm in favour of this ${subject}", 0.95));

        var registry = new AdaptiveTemplateRegistry(
            tokenizerFactory, familyRegistry, strategies, 0.5, fallback, 0.8, newCache());
        registry.register(reviewNamespace);

        registry.match("protocol:review", "I'm in favour of this design");

        var schemas = registry.schemas("protocol:review");
        assertThat(schemas.get(0).patterns()).hasSize(1);
    }

    @Test
    void matchStructural_doesNotTriggerAdaptiveFallback() {
        var counter = new AtomicInteger();
        var fallback = countingFallback(counter, new ClassificationResult.Matched(
            "vote", Map.of("decision", "approve", "subject", "design"),
            "pattern", 0.95));

        var registry = new AdaptiveTemplateRegistry(
            tokenizerFactory, familyRegistry, strategies, 0.5, fallback, 0.8, newCache());
        registry.register(reviewNamespace);

        var result = registry.matchStructural("I'm in favour of this design");
        assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
        assertThat(counter.get()).isEqualTo(0);
    }

    @Test
    void matchWithTrace_includesAdaptiveTier() {
        var fallback = stubFallback(new ClassificationResult.Matched(
            "vote", Map.of("decision", "approve", "subject", "design"),
            "I'm in favour of this ${subject}", 0.95));

        var registry = new AdaptiveTemplateRegistry(
            tokenizerFactory, familyRegistry, strategies, 0.5, fallback, 0.8, newCache());
        registry.register(reviewNamespace);

        var result = registry.matchWithTrace("protocol:review",
            "I'm in favour of this design");
        assertThat(result.result()).isInstanceOf(TemplateMatch.Matched.class);

        var adaptiveCandidate = result.trace().candidates().stream()
            .filter(c -> c.outcome() == CandidateOutcome.ADAPTIVE_MATCHED)
            .findFirst();
        assertThat(adaptiveCandidate).isPresent();
        assertThat(adaptiveCandidate.get().score()).isEqualTo(0.9);
    }

    @Test
    void replaceOrRegister_invalidatesCache() {
        var counter = new AtomicInteger();
        var fallback = countingFallback(counter, new ClassificationResult.Matched(
            "vote", Map.of("decision", "approve", "subject", "design"),
            "pattern", 0.95));

        var cache = newCache();
        var registry = new AdaptiveTemplateRegistry(
            tokenizerFactory, familyRegistry, strategies, 0.5, fallback, 0.8, cache);
        registry.register(reviewNamespace);

        registry.match("protocol:review", "I'm in favour of this design");
        assertThat(counter.get()).isEqualTo(1);

        registry.replaceOrRegister(reviewNamespace);

        registry.match("protocol:review", "I'm in favour of this design");
        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void globalMatch_delegatesToAdaptiveFallback() {
        var fallback = stubFallback(new ClassificationResult.Matched(
            "vote", Map.of("decision", "approve", "subject", "design"),
            "I'm in favour of this ${subject}", 0.95));

        var registry = new AdaptiveTemplateRegistry(
            tokenizerFactory, familyRegistry, strategies, 0.5, fallback, 0.8, newCache());
        registry.register(reviewNamespace);

        var result = registry.match("I'm in favour of this design");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
        assertThat(((TemplateMatch.Matched) result).score()).isEqualTo(0.9);
    }

    private static AdaptiveFallbackMatcher stubFallback(ClassificationResult result) {
        return (input, candidates, lang) -> result;
    }

    private static AdaptiveFallbackMatcher countingFallback(
            AtomicInteger counter, ClassificationResult result) {
        return (input, candidates, lang) -> {
            counter.incrementAndGet();
            return result;
        };
    }
}
