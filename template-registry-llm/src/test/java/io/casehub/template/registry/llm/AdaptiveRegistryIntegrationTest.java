package io.casehub.template.registry.llm;

import io.casehub.template.registry.AdaptiveFallbackMatcher;
import io.casehub.template.registry.CandidateOutcome;
import io.casehub.template.registry.ClassificationResult;
import io.casehub.template.registry.TemplateCandidate;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.VariationSeeder;
import io.casehub.template.registry.nlp.OpenNlpTokenizer;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adaptive Semantic Variation — Integration Tests.
 *
 * <p>These tests prove that the three-tier matching pipeline works as a whole.
 * The template registry handles natural language input through three tiers:
 * <ol>
 *   <li><b>Regex</b> — exact pattern match against canonical + seeded patterns (score 1.0)</li>
 *   <li><b>NLP structural</b> — lemmatised matching handles inflection: "approved" → "approve" (score 0.5–1.0)</li>
 *   <li><b>Adaptive LLM fallback</b> — classifies semantic paraphrases, extracts fields,
 *       and registers learned variations so the structural tiers handle them next time (score 0.9)</li>
 * </ol>
 *
 * <p>The LLM is simulated via stub implementations of {@link AdaptiveFallbackMatcher}.
 * These stubs return predetermined classification results, letting the tests verify
 * the pipeline mechanics — caching, variation registration, collision detection,
 * confidence thresholds — without real LLM calls.
 *
 * <p>The key property: <b>the system gets smarter over time</b>. The first time
 * a novel paraphrase arrives, there's an LLM call. If the variation preserves all
 * variable placeholders, it's registered as a structural pattern. Every subsequent
 * occurrence — including inflected forms — matches via the structural tiers with
 * no LLM involvement.
 */
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

    // ── Seeded Variations ──────────────────────────────────────────────

    /**
     * Registration-time seeding: an LLM generates structural rearrangements of each
     * template pattern before registration. The seeded variations compile as additional
     * regex and NLP patterns — no LLM cost at match time.
     *
     * <p>Seeding handles structural rearrangements where all ${variable} placeholders
     * survive: "I ${decision} this ${subject}" → "This ${subject} gets my ${decision}".
     * It does NOT handle semantic paraphrase where a variable value is replaced by a
     * synonym ("in favour of" for "approve") — that's tier 3's job.
     */
    @Nested
    class SeededVariations {

        @Test
        void structuralRearrangement_matchesViaRegexWithoutLlm() {
            // The seeder returns a rearranged pattern that preserves both ${decision}
            // and ${subject}. This compiles as a regex pattern at registration time.
            VariationSeeder seeder = (def, lang) ->
                List.of("This ${subject} gets my ${decision}");

            // Fallback that throws — proves the LLM is never called
            var registry = new AdaptiveTemplateRegistry(tokenizer,
                (input, candidates, lang) -> { throw new RuntimeException("LLM should not be called"); },
                0.8, newCache());

            var service = new VariationSeedingService(seeder, registry);
            service.seedAndRegister(reviewNamespace());

            // The seeded rearrangement matches via regex (score 1.0)
            var result = registry.match("protocol:review", "This architecture gets my approve");
            assertThat(result)
                .as("seeded rearrangement matches via regex — no LLM call")
                .isInstanceOf(TemplateMatch.Matched.class);
            var matched = (TemplateMatch.Matched) result;
            assertThat(matched.score())
                .as("regex match = 1.0")
                .isEqualTo(1.0);
            assertThat(matched.fields())
                .containsEntry("decision", "approve")
                .containsEntry("subject", "architecture");
        }

        @Test
        void seededVariation_alsoMatchesInflectedFormsViaNlp() {
            // A seeded variation "This ${subject} gets my ${decision}" also benefits
            // from NLP lemmatisation: "got" lemmatises to "get", "approved" to "approve".
            VariationSeeder seeder = (def, lang) ->
                List.of("This ${subject} gets my ${decision}");

            var registry = new AdaptiveTemplateRegistry(tokenizer,
                (input, candidates, lang) -> { throw new RuntimeException("LLM should not be called"); },
                0.8, newCache());

            var service = new VariationSeedingService(seeder, registry);
            service.seedAndRegister(reviewNamespace());

            // "got" is past tense of "gets" — NLP lemmatises it
            var result = registry.match("protocol:review", "This design got my approve");
            assertThat(result)
                .as("inflected seeded variation matches via NLP — no LLM call")
                .isInstanceOf(TemplateMatch.Matched.class);
            var matched = (TemplateMatch.Matched) result;
            assertThat(matched.fields())
                .containsEntry("decision", "approve")
                .containsEntry("subject", "design");
        }

        @Test
        void seededVariation_doesNotBridgeSemanticParaphrase() {
            // "I'm in favour of this design" substitutes a synonym for the enum value.
            // The seeder can rearrange structure but can't bridge vocabulary substitution.
            VariationSeeder seeder = (def, lang) ->
                List.of("This ${subject} gets my ${decision}");

            var registry = new AdaptiveTemplateRegistry(tokenizer,
                (input, candidates, lang) -> new ClassificationResult.Unmatched(),
                0.8, newCache());

            var service = new VariationSeedingService(seeder, registry);
            service.seedAndRegister(reviewNamespace());

            var structural = registry.matchStructural("I'm in favour of this design");
            assertThat(structural)
                .as("semantic paraphrase is NOT handled by tiers 1+2 — 'in favour of'"
                    + " is different words, not a structural rearrangement")
                .isInstanceOf(TemplateMatch.Unmatched.class);
        }

        @Test
        void multipleSeededVariations_allMatch() {
            // A seeder can return multiple rearrangements per template
            VariationSeeder seeder = (def, lang) -> List.of(
                "This ${subject} gets my ${decision}",
                "My ${decision} goes to this ${subject}",
                "${subject} has my ${decision}");

            var registry = new AdaptiveTemplateRegistry(tokenizer,
                (input, candidates, lang) -> { throw new RuntimeException("LLM should not be called"); },
                0.8, newCache());

            var service = new VariationSeedingService(seeder, registry);
            service.seedAndRegister(reviewNamespace());

            // All three rearrangements match via regex
            assertThat(registry.match("protocol:review", "This design gets my approve"))
                .isInstanceOf(TemplateMatch.Matched.class);
            assertThat(registry.match("protocol:review", "My reject goes to this proposal"))
                .isInstanceOf(TemplateMatch.Matched.class);
            assertThat(registry.match("protocol:review", "architecture has my approve"))
                .isInstanceOf(TemplateMatch.Matched.class);

            // Schema shows all 4 patterns (1 canonical + 3 seeded)
            assertThat(registry.schemas("protocol:review").get(0).patterns()).hasSize(4);
        }
    }

    // ── Adaptive Fallback — Crossing the Paraphrase Cliff ──────────────

    /**
     * Tier 3: when both regex and NLP return {@code Unmatched}, the adaptive fallback
     * sends the input to an LLM for semantic classification. The LLM returns which
     * template matches, the extracted field values, and a suggested variation pattern.
     *
     * <p>This is where the paraphrase cliff is crossed. "I'm in favour of this design"
     * is semantically equivalent to "I approve this design" but structurally different —
     * the NLP can't bridge it because "in favour of" shares no lemma with "approve".
     * The LLM understands the semantic equivalence and classifies it correctly.
     */
    @Nested
    class CrossingTheParaphraseCliff {

        @ParameterizedTest(name = "paraphrase matched via LLM: \"{0}\"")
        @ValueSource(strings = {
            "I'm in favour of this design",
            "This design has my approval",
            "I give this design the green light",
            "I support this proposal fully",
            "Thumbs up on this architecture",
            "I'm on board with this approach"
        })
        void semanticParaphrases_matchViaAdaptiveFallback(String input) {
            // Simulated LLM: always classifies as vote/approve for these paraphrases.
            // A real LLM would inspect the input semantically; the stub proves the
            // pipeline mechanics — the classifier is called, fields are extracted,
            // the result is returned as a Matched with score 0.9.
            AdaptiveFallbackMatcher fallback = (in, candidates, lang) ->
                new ClassificationResult.Matched("vote",
                    Map.of("decision", "approve", "subject", extractSubject(in)),
                    "I'm in favour of this ${subject}", 0.95);

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            var result = registry.match("protocol:review", input);
            assertThat(result)
                .as("'%s' is semantically equivalent to 'I approve this ${subject}'"
                    + " — tier 3 LLM classifier bridges the gap", input)
                .isInstanceOf(TemplateMatch.Matched.class);
            var matched = (TemplateMatch.Matched) result;
            assertThat(matched.templateName()).isEqualTo("vote");
            assertThat(matched.fields()).containsEntry("decision", "approve");
            assertThat(matched.score())
                .as("adaptive match score is 0.9 — lower than regex (1.0),"
                    + " distinguishable in downstream processing")
                .isEqualTo(0.9);
            assertThat(matched.patternIndex())
                .as("pattern index is -1 — not from the compiled pattern set")
                .isEqualTo(-1);
        }

        @Test
        void regexAndNlpTiersStillWorkWithAdaptiveRegistry() {
            // The adaptive registry is a strict extension — tiers 1 and 2 are unaffected.
            var llmCallCount = new AtomicInteger();
            AdaptiveFallbackMatcher fallback = (in, candidates, lang) -> {
                llmCallCount.incrementAndGet();
                return new ClassificationResult.Unmatched();
            };

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            // Tier 1: regex exact match — LLM never called
            var exact = registry.match("protocol:review", "I approve this design");
            assertThat(exact).isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) exact).score())
                .as("regex tier match = 1.0")
                .isEqualTo(1.0);
            assertThat(llmCallCount.get()).as("LLM not called for regex match").isEqualTo(0);

            // Tier 2: NLP inflection — LLM never called
            var inflected = registry.match("protocol:review", "I approved this design");
            assertThat(inflected).isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) inflected).fields())
                .containsEntry("decision", "approve");
            assertThat(llmCallCount.get()).as("LLM not called for NLP match").isEqualTo(0);
        }
    }

    // ── Adaptive Learning — The System Gets Smarter ────────────────────

    /**
     * The adaptive fallback doesn't just classify — it teaches the registry.
     * When the LLM returns a {@code suggestedVariation} that preserves all
     * variable placeholders, the registry registers it as a new structural pattern.
     *
     * <p>This is the self-improving property: the first occurrence of a novel
     * phrasing costs an LLM call. Every subsequent occurrence — including inflected
     * forms — matches structurally with no LLM involvement.
     */
    @Nested
    class AdaptiveLearning {

        /**
         * The core adaptive learning proof: a novel phrasing triggers an LLM call,
         * the variation is registered, and a completely different input using the
         * same sentence structure matches via regex with no LLM involvement.
         *
         * <p>This is the property that makes the system self-improving. The first
         * "This design gets my approve" costs one LLM call. After that,
         * "This architecture gets my reject" — different subject, different decision,
         * same structure — matches via regex at score 1.0. The LLM call count stays
         * at 1, proving the structural matcher is handling it.
         */
        @Test
        void learnedVariation_matchesDifferentValuesWithoutLlm() {
            var llmCallCount = new AtomicInteger();
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) -> {
                llmCallCount.incrementAndGet();
                return new ClassificationResult.Matched("vote",
                    Map.of("decision", "approve", "subject", "design"),
                    "This ${subject} gets my ${decision}", 0.95);
            };

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            // ── First occurrence: "This design gets my approve" ──
            // No regex pattern matches (canonical is "I ${decision} this ${subject}").
            // No NLP pattern matches (structural mismatch — word order is different).
            // Tier 3 fires: LLM classifies it as vote, extracts fields, suggests
            // the variation "This ${subject} gets my ${decision}".
            var first = registry.match("protocol:review", "This design gets my approve");
            assertThat(first)
                .as("'This design gets my approve' — tiers 1+2 miss, tier 3 classifies via LLM")
                .isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) first).score())
                .as("score 0.9 distinguishes LLM-classified matches from structural (1.0)")
                .isEqualTo(0.9);
            assertThat(((TemplateMatch.Matched) first).templateName())
                .as("LLM correctly identifies the 'vote' template")
                .isEqualTo("vote");
            assertThat(((TemplateMatch.Matched) first).fields())
                .as("LLM extracts both field values from the novel phrasing")
                .containsEntry("decision", "approve")
                .containsEntry("subject", "design");
            assertThat(llmCallCount.get())
                .as("exactly one LLM call — the first occurrence of this phrasing")
                .isEqualTo(1);

            // ── The variation "This ${subject} gets my ${decision}" is now registered ──
            // Verify it appears in the schema alongside the canonical pattern
            assertThat(registry.schemas("protocol:review").get(0).patterns())
                .as("registry now has 2 patterns: canonical + learned variation")
                .hasSize(2)
                .containsExactly(
                    "I ${decision} this ${subject}",
                    "This ${subject} gets my ${decision}");

            // ── Second occurrence: different values, same learned structure ──
            // "This architecture gets my reject" uses the LEARNED pattern with
            // completely different variable values. If the variation was registered
            // correctly, this matches via regex (score 1.0) with no LLM call.
            var second = registry.match("protocol:review", "This architecture gets my reject");
            assertThat(second)
                .as("'This architecture gets my reject' — matches learned pattern via regex")
                .isInstanceOf(TemplateMatch.Matched.class);
            var matched = (TemplateMatch.Matched) second;
            assertThat(matched.score())
                .as("score 1.0 proves regex handled it, not LLM (which returns 0.9)")
                .isEqualTo(1.0);
            assertThat(matched.fields())
                .as("field extraction works correctly from the learned pattern")
                .containsEntry("decision", "reject")
                .containsEntry("subject", "architecture");
            assertThat(matched.patternIndex())
                .as("pattern index 1 = second pattern (the learned variation)")
                .isEqualTo(1);
            assertThat(llmCallCount.get())
                .as("LLM call count still 1 — the learned variation handled it structurally")
                .isEqualTo(1);
        }

        /**
         * After a variation is learned, the NLP tier extends it to inflected forms
         * automatically. This is the compounding benefit: the LLM teaches the registry
         * one pattern, and NLP lemmatisation generalises it to all tense/number variants.
         *
         * <p>Learned pattern: "This ${subject} gets my ${decision}"
         * <br>Inflected input: "This proposal <b>got</b> my reject"
         * <br>OpenNLP lemmatises "got" → "get", which matches "gets" → "get".
         * The NLP structural matcher finds the alignment. No LLM call needed.
         *
         * <p>This demonstrates that adaptive learning multiplies — each learned
         * variation doesn't just match literally, it matches all inflected forms
         * through the NLP tier. A single LLM call covers dozens of surface forms.
         */
        @Test
        void learnedVariation_alsoHandlesInflectedFormsViaNlp() {
            var llmCallCount = new AtomicInteger();
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) -> {
                llmCallCount.incrementAndGet();
                return new ClassificationResult.Matched("vote",
                    Map.of("decision", "approve", "subject", "design"),
                    "This ${subject} gets my ${decision}", 0.95);
            };

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            // Trigger learning: "This design gets my approve" → LLM classifies,
            // registers "This ${subject} gets my ${decision}" as a structural pattern
            registry.match("protocol:review", "This design gets my approve");
            assertThat(llmCallCount.get())
                .as("learning triggered — one LLM call")
                .isEqualTo(1);

            // Inflected form of the LEARNED pattern: "got" instead of "gets".
            // OpenNLP lemmatises both to "get", so the NLP structural matcher
            // finds the alignment without any LLM involvement.
            var inflected = registry.match("protocol:review", "This proposal got my reject");
            assertThat(inflected)
                .as("'This proposal got my reject' — inflected form of learned pattern,"
                    + " handled by NLP lemmatisation (got → get = gets → get)")
                .isInstanceOf(TemplateMatch.Matched.class);
            var matched = (TemplateMatch.Matched) inflected;
            assertThat(matched.score())
                .as("NLP match score — below 1.0 (not regex) but above threshold")
                .isGreaterThan(0.0)
                .isLessThanOrEqualTo(1.0);
            assertThat(matched.fields())
                .as("correct field extraction from the inflected learned pattern")
                .containsEntry("decision", "reject")
                .containsEntry("subject", "proposal");
            assertThat(matched.patternIndex())
                .as("pattern index >= 0 — matched from compiled NLP patterns, not LLM")
                .isGreaterThanOrEqualTo(0);
            assertThat(llmCallCount.get())
                .as("LLM call count still 1 — NLP handled the inflection entirely")
                .isEqualTo(1);
        }

        @Test
        void multipleVariationsCanBeLearnedForSameTemplate() {
            // The registry can learn multiple phrasings for the same template.
            // Each costs one LLM call; subsequent occurrences of any learned
            // phrasing are free.
            var llmCallCount = new AtomicInteger();
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) -> {
                llmCallCount.incrementAndGet();
                if (input.startsWith("This")) {
                    return new ClassificationResult.Matched("vote",
                        Map.of("decision", "approve", "subject", "design"),
                        "This ${subject} gets my ${decision}", 0.95);
                } else {
                    return new ClassificationResult.Matched("vote",
                        Map.of("decision", "approve", "subject", "design"),
                        "My ${decision} goes to ${subject}", 0.95);
                }
            };

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            // Learn first variation
            registry.match("protocol:review", "This design gets my approve");
            assertThat(llmCallCount.get()).isEqualTo(1);

            // Learn second variation
            registry.match("protocol:review", "My approve goes to design");
            assertThat(llmCallCount.get()).isEqualTo(2);

            // Both learned patterns now match structurally — no more LLM calls
            var r1 = registry.match("protocol:review", "This architecture gets my reject");
            assertThat(r1).isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) r1).score())
                .as("first learned pattern — regex match")
                .isEqualTo(1.0);

            var r2 = registry.match("protocol:review", "My reject goes to proposal");
            assertThat(r2).isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) r2).score())
                .as("second learned pattern — regex match")
                .isEqualTo(1.0);

            // Schema shows all 3 patterns (1 canonical + 2 learned)
            assertThat(registry.schemas("protocol:review").get(0).patterns()).hasSize(3);

            assertThat(llmCallCount.get())
                .as("LLM called exactly twice — once per novel phrasing, never again")
                .isEqualTo(2);
        }
    }

    // ── Classification Cache ───────────────────────────────────────────

    /**
     * Not every LLM classification can be turned into a learned variation.
     * When the suggested variation drops a variable placeholder (e.g. "I'm in
     * favour of this ${subject}" drops ${decision} — the enum value "approve"
     * is baked into "in favour of"), the variation can't be registered as a
     * structural pattern.
     *
     * <p>For these cases, the classification cache prevents repeated LLM calls.
     * Both positive results (matched but not registrable) and negative results
     * (genuinely unrelated input) are cached with configurable TTLs.
     */
    @Nested
    class ClassificationCaching {

        /**
         * When the LLM suggests a variation that drops a variable placeholder, it
         * can't be registered as a structural pattern. "I'm in favour of this ${subject}"
         * drops ${decision} — the enum value "approve" is baked into the phrasing
         * "in favour of". The registry can't generalise from that.
         *
         * <p>But the classification result itself IS cached. If the same user says
         * "I'm in favour of this design" ten more times, every occurrence after the
         * first is served from cache with no LLM call. The match is still returned
         * (it's semantically correct) — only the structural registration is skipped.
         */
        @Test
        void nonRegistrableVariation_cachedForRepeatedInput() {
            var llmCallCount = new AtomicInteger();
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) -> {
                llmCallCount.incrementAndGet();
                return new ClassificationResult.Matched("vote",
                    Map.of("decision", "approve", "subject", "design"),
                    "I'm in favour of this ${subject}", 0.95);
            };

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            // First call: LLM classifies. Variation drops ${decision}, not registered.
            var first = registry.match("protocol:review", "I'm in favour of this design");
            assertThat(first).isInstanceOf(TemplateMatch.Matched.class);
            assertThat(llmCallCount.get()).isEqualTo(1);

            // Pattern NOT registered (only 1 pattern — the original canonical)
            assertThat(registry.schemas("protocol:review").get(0).patterns())
                .as("variation drops ${decision} — not registered as structural pattern")
                .hasSize(1);

            // Second and third calls: cache serves the result, no LLM call
            registry.match("protocol:review", "I'm in favour of this design");
            registry.match("protocol:review", "I'm in favour of this design");
            assertThat(llmCallCount.get())
                .as("cache prevents repeated LLM calls for non-registrable paraphrases")
                .isEqualTo(1);
        }

        @Test
        void negativeResult_cachedToAvoidRepeatedMisses() {
            // Genuinely unrelated input: the LLM says "no match". Cached as a negative
            // result so the same garbage input doesn't trigger another LLM call.
            var llmCallCount = new AtomicInteger();
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) -> {
                llmCallCount.incrementAndGet();
                return new ClassificationResult.Unmatched();
            };

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            registry.match("protocol:review", "The weather is nice today");
            registry.match("protocol:review", "The weather is nice today");
            registry.match("protocol:review", "The weather is nice today");

            assertThat(llmCallCount.get())
                .as("negative result cached — LLM called once for unrelated input")
                .isEqualTo(1);
        }

        @Test
        void cacheInvalidatedOnNamespaceReplace() {
            // When a namespace is replaced (templates change), cached results become
            // stale. The cache is invalidated per-namespace on replaceOrRegister.
            var llmCallCount = new AtomicInteger();
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) -> {
                llmCallCount.incrementAndGet();
                return new ClassificationResult.Matched("vote",
                    Map.of("decision", "approve", "subject", "design"),
                    "I'm in favour of this ${subject}", 0.95);
            };

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            // Populate cache
            registry.match("protocol:review", "I'm in favour of this design");
            assertThat(llmCallCount.get()).isEqualTo(1);

            // Replace namespace — cache invalidated
            registry.replaceOrRegister(reviewNamespace());

            // Same input: cache was cleared, LLM called again
            registry.match("protocol:review", "I'm in favour of this design");
            assertThat(llmCallCount.get())
                .as("cache invalidated by replaceOrRegister — LLM called again")
                .isEqualTo(2);
        }
    }

    // ── Confidence Threshold ───────────────────────────────────────────

    /**
     * Low-confidence LLM classifications are returned to the caller (the match
     * result is still useful) but the suggested variation is NOT registered.
     * This prevents unreliable classifications from polluting the structural
     * pattern set.
     */
    @Nested
    class ConfidenceGating {

        @Test
        void lowConfidence_returnsMatchButDoesNotLearn() {
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) ->
                new ClassificationResult.Matched("vote",
                    Map.of("decision", "approve", "subject", "design"),
                    "This ${subject} gets my ${decision}", 0.5);

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            // The match is returned — useful even at low confidence
            var result = registry.match("protocol:review", "This design gets my approve");
            assertThat(result)
                .as("low-confidence match still returned to caller")
                .isInstanceOf(TemplateMatch.Matched.class);

            // But the variation is NOT registered
            assertThat(registry.schemas("protocol:review").get(0).patterns())
                .as("low confidence (0.5 < 0.8 threshold) — variation not learned")
                .hasSize(1);
        }

        @Test
        void highConfidence_registersVariation() {
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) ->
                new ClassificationResult.Matched("vote",
                    Map.of("decision", "approve", "subject", "design"),
                    "This ${subject} gets my ${decision}", 0.95);

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            registry.match("protocol:review", "This design gets my approve");

            assertThat(registry.schemas("protocol:review").get(0).patterns())
                .as("high confidence (0.95 >= 0.8 threshold) — variation learned")
                .hasSize(2)
                .contains("This ${subject} gets my ${decision}");
        }
    }

    // ── Collision Guard ────────────────────────────────────────────────

    /**
     * When the LLM suggests a variation that would match a template in a DIFFERENT
     * namespace, the variation is rejected. The match result is still returned
     * (the classification was correct), but the cross-namespace collision is not
     * persisted.
     *
     * <p>Similarly, the seeding service checks every generated variation against
     * all existing templates before registration. Colliding variations are logged
     * and filtered out.
     */
    @Nested
    class CollisionGuard {

        @Test
        void adaptiveFallback_rejectsCollidingVariation() {
            // ns1: "I ${action} this ${thing}" with string variables
            var ns1 = new TemplateNamespace("ns1", Map.of("t1",
                TemplateDefinition.builder("t1")
                    .pattern("I ${action} this ${thing}")
                    .variable("action", VariableConstraint.string())
                    .variable("thing", VariableConstraint.string())
                    .build()));

            // The LLM suggests a variation identical to ns1's pattern — collision
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) ->
                new ClassificationResult.Matched("t2",
                    Map.of("action", "run", "thing", "test"),
                    "I ${action} this ${thing}", 0.95);

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(ns1);

            // ns2 has a different canonical pattern but the LLM suggests one that collides
            var ns2 = new TemplateNamespace("ns2", Map.of("t2",
                TemplateDefinition.builder("t2")
                    .pattern("${thing} receives my ${action}")
                    .variable("action", VariableConstraint.string())
                    .variable("thing", VariableConstraint.string())
                    .build()));
            registry.register(ns2);

            registry.match("ns2", "some unmatched input");

            assertThat(registry.schemas("ns2").get(0).patterns())
                .as("colliding variation rejected — only canonical pattern remains")
                .hasSize(1);
        }

        @Test
        void seedingService_filtersCollidingVariations() {
            var registry = new AdaptiveTemplateRegistry(tokenizer,
                (i, c, l) -> new ClassificationResult.Unmatched(), 0.8, newCache());

            // Register ns1 first
            var ns1 = new TemplateNamespace("ns1", Map.of("t1",
                TemplateDefinition.builder("t1")
                    .pattern("I ${action} this ${thing}")
                    .variable("action", VariableConstraint.string())
                    .variable("thing", VariableConstraint.string())
                    .build()));
            registry.register(ns1);

            // Seeder returns a variation identical to ns1's pattern — collision
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
            assertThat(registry.schemas("ns2").get(0).patterns())
                .as("colliding seeded variation filtered out — only canonical pattern")
                .hasSize(1);
        }
    }

    // ── Error Resilience ───────────────────────────────────────────────

    /**
     * When the LLM is unavailable (network error, rate limit, timeout), the
     * adaptive fallback degrades gracefully to Unmatched. The structural tiers
     * continue working. No exception propagates to the caller.
     */
    @Nested
    class ErrorResilience {

        @Test
        void llmUnavailable_degradesToUnmatched() {
            AdaptiveFallbackMatcher failingFallback =
                (input, candidates, lang) -> { throw new RuntimeException("LLM service unavailable"); };

            var registry = new AdaptiveTemplateRegistry(
                tokenizer, failingFallback, 0.8, newCache());
            registry.register(reviewNamespace());

            // Structural tiers miss, LLM throws — graceful degradation
            var result = registry.match("protocol:review", "I'm in favour of this design");
            assertThat(result)
                .as("LLM failure degrades to Unmatched — no exception propagated")
                .isInstanceOf(TemplateMatch.Unmatched.class);

            // Structural tiers still work fine
            var exact = registry.match("protocol:review", "I approve this design");
            assertThat(exact)
                .as("regex tier unaffected by LLM failure")
                .isInstanceOf(TemplateMatch.Matched.class);
        }

        @Test
        void llmReturnsUnknownTemplateName_treatedAsUnmatched() {
            // LLM hallucination: returns a template name that doesn't exist
            AdaptiveFallbackMatcher hallucinating = (input, candidates, lang) ->
                new ClassificationResult.Matched("nonexistent_template",
                    Map.of("decision", "approve"),
                    "pattern", 0.95);

            var registry = new AdaptiveTemplateRegistry(
                tokenizer, hallucinating, 0.8, newCache());
            registry.register(reviewNamespace());

            var result = registry.match("protocol:review", "I'm in favour of this design");
            assertThat(result)
                .as("hallucinated template name → Unmatched, no exception")
                .isInstanceOf(TemplateMatch.Unmatched.class);
        }
    }

    // ── Observability ──────────────────────────────────────────────────

    /**
     * The trace API ({@code matchWithTrace}) includes adaptive tier results
     * alongside regex and NLP candidates. The {@code ADAPTIVE_MATCHED} outcome
     * distinguishes LLM-classified matches in diagnostic output.
     */
    @Nested
    class Observability {

        @Test
        void matchWithTrace_includesAdaptiveTierInDiagnostics() {
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) ->
                new ClassificationResult.Matched("vote",
                    Map.of("decision", "approve", "subject", "design"),
                    "I'm in favour of this ${subject}", 0.95);

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            var result = registry.matchWithTrace("protocol:review",
                "I'm in favour of this design");
            assertThat(result.result()).isInstanceOf(TemplateMatch.Matched.class);

            // The trace shows structural tier candidates (regex and NLP) PLUS the
            // adaptive tier result — all three tiers visible in one trace
            var adaptiveCandidate = result.trace().candidates().stream()
                .filter(c -> c.outcome() == CandidateOutcome.ADAPTIVE_MATCHED)
                .findFirst();
            assertThat(adaptiveCandidate)
                .as("ADAPTIVE_MATCHED appears in trace output")
                .isPresent();
            assertThat(adaptiveCandidate.get().score()).isEqualTo(0.9);

            // Structural candidates also present — the trace shows the full pipeline
            var structuralCandidates = result.trace().candidates().stream()
                .filter(c -> c.outcome() != CandidateOutcome.ADAPTIVE_MATCHED)
                .toList();
            assertThat(structuralCandidates)
                .as("structural tier candidates visible alongside adaptive result")
                .isNotEmpty();
        }
    }

    // ── Full Pipeline Scenario ─────────────────────────────────────────

    /**
     * End-to-end: canonical → inflection → paraphrase → learning → reuse.
     *
     * <p>This test walks through the complete lifecycle of a template in the
     * adaptive registry. It proves every tier working together in sequence,
     * and demonstrates the self-improving property across multiple inputs.
     *
     * <p>The scenario:
     * <ol>
     *   <li>Register a vote template with the canonical pattern</li>
     *   <li>Match exact input → tier 1 regex (score 1.0, 0 LLM calls)</li>
     *   <li>Match inflected input → tier 2 NLP (score &lt; 1.0, 0 LLM calls)</li>
     *   <li>Match paraphrase with preserved variables → tier 3 LLM (score 0.9, 1 LLM call)</li>
     *   <li>Variation registered. Same pattern, different values → tier 1 regex (score 1.0, still 1 LLM call)</li>
     *   <li>Inflected form of the learned pattern → tier 2 NLP (still 1 LLM call)</li>
     *   <li>Second novel paraphrase → tier 3 LLM again (2 LLM calls total)</li>
     *   <li>Non-registrable paraphrase (drops variable) → tier 3 LLM, cached (3 LLM calls)</li>
     *   <li>Same non-registrable paraphrase repeated → cache hit (still 3 LLM calls)</li>
     *   <li>Unrelated input → Unmatched, negative cached (4 LLM calls)</li>
     * </ol>
     */
    @Nested
    class FullPipelineScenario {

        @Test
        void completeLifecycle() {
            var llmCallCount = new AtomicInteger();

            // The simulated LLM: responds based on what it recognises
            AdaptiveFallbackMatcher fallback = (input, candidates, lang) -> {
                llmCallCount.incrementAndGet();
                if (input.contains("gets my") || input.contains("got my")) {
                    return new ClassificationResult.Matched("vote",
                        Map.of("decision", "approve", "subject", "design"),
                        "This ${subject} gets my ${decision}", 0.95);
                }
                if (input.contains("goes to")) {
                    return new ClassificationResult.Matched("vote",
                        Map.of("decision", "reject", "subject", "proposal"),
                        "My ${decision} goes to ${subject}", 0.92);
                }
                if (input.contains("in favour")) {
                    return new ClassificationResult.Matched("vote",
                        Map.of("decision", "approve", "subject", "design"),
                        "I'm in favour of this ${subject}", 0.90);
                }
                return new ClassificationResult.Unmatched();
            };

            var registry = new AdaptiveTemplateRegistry(tokenizer, fallback, 0.8, newCache());
            registry.register(reviewNamespace());

            // ── Step 1: Exact canonical match → Tier 1 (regex) ──
            // "I approve this design" matches the canonical pattern exactly.
            // The regex tier handles it at score 1.0. No NLP, no LLM.
            var exact = registry.match("protocol:review", "I approve this design");
            assertThat(exact)
                .as("step 1: 'I approve this design' — exact canonical match via regex")
                .isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) exact).score())
                .as("step 1: regex tier always produces score 1.0")
                .isEqualTo(1.0);
            assertThat(((TemplateMatch.Matched) exact).fields())
                .as("step 1: fields extracted correctly from canonical pattern")
                .containsEntry("decision", "approve")
                .containsEntry("subject", "design");
            assertThat(llmCallCount.get())
                .as("step 1: LLM never involved — regex handled it")
                .isEqualTo(0);

            // ── Step 2: Inflected canonical → Tier 2 (NLP) ──
            // "I approved this design" — past tense "approved" doesn't match the
            // regex (which expects literal "approve"). NLP lemmatises "approved" → "approve"
            // and finds the structural alignment.
            var inflected = registry.match("protocol:review", "I approved this design");
            assertThat(inflected)
                .as("step 2: 'I approved this design' — NLP lemmatises 'approved' → 'approve'")
                .isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) inflected).fields())
                .as("step 2: NLP extracts the lemmatised enum value, not the surface form")
                .containsEntry("decision", "approve");
            assertThat(llmCallCount.get())
                .as("step 2: LLM still not called — NLP handled inflection")
                .isEqualTo(0);

            // ── Step 3: Novel structural paraphrase → Tier 3 (LLM) → learns ──
            // "This design gets my approve" rearranges the sentence structure entirely.
            // Regex fails (different word order). NLP fails (different structure, not just
            // inflection). Tier 3 fires: the simulated LLM classifies it as a vote,
            // extracts the fields, and returns the variation "This ${subject} gets my ${decision}".
            // The registry registers this as a new structural pattern.
            var paraphrase1 = registry.match("protocol:review", "This design gets my approve");
            assertThat(paraphrase1)
                .as("step 3: 'This design gets my approve' — tiers 1+2 fail, tier 3 LLM classifies")
                .isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) paraphrase1).score())
                .as("step 3: score 0.9 = LLM classification (regex would be 1.0)")
                .isEqualTo(0.9);
            assertThat(llmCallCount.get())
                .as("step 3: first LLM call of the scenario")
                .isEqualTo(1);
            assertThat(registry.schemas("protocol:review").get(0).patterns())
                .as("step 3: variation learned — registry now has 2 patterns")
                .hasSize(2);

            // ── Step 4: Different values on the learned pattern → Tier 1 (regex) ──
            // "This architecture gets my reject" uses the LEARNED pattern with different
            // variable values. The learned variation was compiled as a regex pattern at
            // registration time, so the regex tier catches it at score 1.0.
            var reuse = registry.match("protocol:review", "This architecture gets my reject");
            assertThat(reuse)
                .as("step 4: 'This architecture gets my reject' — learned pattern matches via regex")
                .isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) reuse).score())
                .as("step 4: score 1.0 proves regex handled it (LLM would return 0.9)")
                .isEqualTo(1.0);
            assertThat(((TemplateMatch.Matched) reuse).fields())
                .as("step 4: different variable values extracted correctly from learned pattern")
                .containsEntry("decision", "reject")
                .containsEntry("subject", "architecture");
            assertThat(llmCallCount.get())
                .as("step 4: LLM call count still 1 — the learned variation handled this structurally")
                .isEqualTo(1);

            // ── Step 5: Inflected form of the learned pattern → Tier 2 (NLP) ──
            // "This proposal got my reject" — "got" is past tense of "gets".
            // The regex tier fails (expects "gets", sees "got"). The NLP tier
            // lemmatises both to "get" and finds the structural match.
            // This proves NLP extends learned patterns to ALL their inflected forms.
            var learnedInflected = registry.match("protocol:review",
                "This proposal got my reject");
            assertThat(learnedInflected)
                .as("step 5: 'This proposal got my reject' — NLP lemmatises 'got' on the learned pattern")
                .isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) learnedInflected).fields())
                .as("step 5: fields extracted from inflected learned pattern")
                .containsEntry("decision", "reject")
                .containsEntry("subject", "proposal");
            assertThat(llmCallCount.get())
                .as("step 5: LLM still at 1 — NLP handled inflection on the learned pattern")
                .isEqualTo(1);

            // ── Step 6: Second novel paraphrase → Tier 3 (LLM) → learns another ──
            // "My reject goes to proposal" is a completely different sentence structure.
            // Neither the canonical pattern nor the first learned pattern covers it.
            // Tier 3 fires again, classifies, and learns "My ${decision} goes to ${subject}".
            var paraphrase2 = registry.match("protocol:review", "My reject goes to proposal");
            assertThat(paraphrase2)
                .as("step 6: 'My reject goes to proposal' — second novel structure, tier 3 fires")
                .isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) paraphrase2).score())
                .as("step 6: score 0.9 = LLM classification again")
                .isEqualTo(0.9);
            assertThat(llmCallCount.get())
                .as("step 6: second LLM call — one per genuinely novel structure")
                .isEqualTo(2);
            assertThat(registry.schemas("protocol:review").get(0).patterns())
                .as("step 6: second variation learned — registry now has 3 patterns")
                .hasSize(3);

            // ── Step 7: Semantic paraphrase that drops a variable → matched but NOT learned ──
            // "I'm in favour of this design" substitutes "in favour of" for the enum value
            // "approve". The LLM classifies it correctly, but the suggested variation
            // "I'm in favour of this ${subject}" drops the ${decision} placeholder (the
            // enum value is baked in). The variation can't be registered as a structural
            // pattern — but the match result IS returned and cached.
            var nonRegistrable = registry.match("protocol:review",
                "I'm in favour of this design");
            assertThat(nonRegistrable)
                .as("step 7: 'I'm in favour of this design' — LLM classifies, but variation drops ${decision}")
                .isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) nonRegistrable).fields())
                .as("step 7: fields extracted correctly despite non-registrable variation")
                .containsEntry("decision", "approve")
                .containsEntry("subject", "design");
            assertThat(llmCallCount.get())
                .as("step 7: third LLM call — non-registrable paraphrase still needs classification")
                .isEqualTo(3);
            assertThat(registry.schemas("protocol:review").get(0).patterns())
                .as("step 7: still 3 patterns — the non-registrable variation was NOT added")
                .hasSize(3);

            // ── Step 8: Same non-registrable input repeated → cache hit ──
            // The classification cache stores the positive result from step 7.
            // Same input → cache hit → match returned without LLM call.
            var cached = registry.match("protocol:review", "I'm in favour of this design");
            assertThat(cached)
                .as("step 8: same non-registrable paraphrase — served from cache")
                .isInstanceOf(TemplateMatch.Matched.class);
            assertThat(llmCallCount.get())
                .as("step 8: LLM call count still 3 — cache prevented a fourth call")
                .isEqualTo(3);

            // ── Step 9: Unrelated input → Tier 3 says Unmatched → negative cached ──
            // "The weather is nice" has nothing to do with voting. All three tiers
            // fail. The LLM returns Unmatched. The negative result is cached to
            // prevent repeated LLM calls for the same garbage input.
            var unrelated = registry.match("protocol:review", "The weather is nice");
            assertThat(unrelated)
                .as("step 9: 'The weather is nice' — genuinely unrelated, all tiers fail")
                .isInstanceOf(TemplateMatch.Unmatched.class);
            assertThat(llmCallCount.get())
                .as("step 9: fourth LLM call — classifier confirms no match")
                .isEqualTo(4);

            // ── Step 10: Same unrelated input repeated → negative cache hit ──
            // The negative cache entry from step 9 prevents another LLM call.
            var cachedNegative = registry.match("protocol:review", "The weather is nice");
            assertThat(cachedNegative)
                .as("step 10: same unrelated input — negative cache serves Unmatched")
                .isInstanceOf(TemplateMatch.Unmatched.class);
            assertThat(llmCallCount.get())
                .as("step 10: LLM call count still 4 — negative cache prevented a fifth call."
                    + " Final tally: 4 LLM calls across 10 match attempts, covering"
                    + " 2 learned variations, 1 cached non-registrable, and 1 cached negative.")
                .isEqualTo(4);
        }
    }

    private static String extractSubject(String input) {
        for (String word : List.of("design", "proposal", "architecture", "approach")) {
            if (input.toLowerCase().contains(word)) return word;
        }
        return "design";
    }
}
