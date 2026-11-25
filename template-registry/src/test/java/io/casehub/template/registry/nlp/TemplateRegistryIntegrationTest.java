package io.casehub.template.registry.nlp;

import io.casehub.template.registry.InMemoryTemplateRegistry;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRegistryIntegrationTest {

    private NlpTemplateRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NlpTemplateRegistry(new OpenNlpTokenizer());
    }

    private TemplateNamespace reviewNamespace() {
        var vote = TemplateDefinition.builder("vote")
            .pattern("I ${decision} this ${subject}[ because ${rationale}]")
            .variable("decision", VariableConstraint.enumOf(
                "approve", "reject", "abstain"))
            .variable("subject", VariableConstraint.string())
            .variable("rationale", VariableConstraint.string().optional())
            .build();
        var escalate = TemplateDefinition.builder("escalate")
            .pattern("Escalating to ${target}[ due to ${reason}]")
            .variable("target", VariableConstraint.string())
            .variable("reason", VariableConstraint.string().optional())
            .build();
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("vote", vote);
        templates.put("escalate", escalate);
        return new TemplateNamespace("protocol:review", templates);
    }

    private void registerProtocol() {
        registry.register(reviewNamespace());
    }

    /**
     * NLP Normalisation — the core proof that NLP adds value over regex.
     *
     * <p>The template "I ${decision} this ${subject}" compiles to a regex that expects
     * the literal word to match enum values exactly: "approve", "reject", or "abstain".
     * When an LLM writes "I approved this design" or "I approves this design", the
     * regex fails because "approved" ≠ "approve" as a string comparison.
     *
     * <p>The NLP pipeline fixes this through <b>lemmatisation</b> — reducing inflected
     * words to their base form (lemma). OpenNLP's lemmatiser maps:
     * <ul>
     *   <li>"approved" (past tense) → "approve"</li>
     *   <li>"approves" (3rd person) → "approve"</li>
     *   <li>"approving" (gerund) → "approve"</li>
     *   <li>"rejected" → "reject", "rejects" → "reject", etc.</li>
     * </ul>
     *
     * <p>Each test in this class creates BOTH a regex-only registry and an NLP registry,
     * asserts the regex returns {@code Unmatched}, then asserts the NLP returns
     * {@code Matched} with the correct lemma as the extracted value. This contrast
     * is the evidence that NLP normalisation is working.
     */
    @Nested
    class NlpNormalisation {

        @BeforeEach void init() { registerProtocol(); }

        @ParameterizedTest(name = "\"{0}\" normalises to decision={1}")
        @CsvSource({
            "I approved this design,   approve",
            "I approves this design,   approve",
            "I approving this design,  approve",
            "I rejected this proposal, reject",
            "I rejects this proposal,  reject",
            "I rejecting this proposal,reject"
        })
        void verbTenseNormalisesToLemma(String input, String expectedLemma) {
            // REGEX rejects: inflected verb != enum value
            var regexOnly = new InMemoryTemplateRegistry();
            regexOnly.register(reviewNamespace());
            assertThat(regexOnly.match("protocol:review", input))
                .as("regex cannot handle inflected verb in '%s'", input)
                .isInstanceOf(TemplateMatch.Unmatched.class);

            // NLP matches: lemmatisation normalises the verb
            var nlpResult = registry.match("protocol:review", input);
            assertThat(nlpResult)
                .as("NLP lemmatises verb in '%s'", input)
                .isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) nlpResult).fields().get("decision"))
                .as("'%s' normalises to '%s'", input, expectedLemma)
                .isEqualTo(expectedLemma);
        }

        @Test
        void pluralDeterminerNormalises() {
            // "these" != "this" in regex literal → regex fails
            // NLP matches "these" via lemma equivalence with "this"
            var regexOnly = new InMemoryTemplateRegistry();
            regexOnly.register(reviewNamespace());
            assertThat(regexOnly.match("protocol:review", "I approve these designs"))
                .isInstanceOf(TemplateMatch.Unmatched.class);

            var nlpResult = registry.match("protocol:review", "I approve these designs");
            assertThat(nlpResult).isInstanceOf(TemplateMatch.Matched.class);
        }

        @Test
        void regexExactMatchBypassesNlp() {
            var r = registry.match("protocol:review", "I approve this design");
            assertThat(r).isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) r).score())
                .as("exact match = regex fast-path, score 1.0")
                .isEqualTo(1.0);
        }

        @Test
        void nlpRejectsWrongEnumEvenWhenStructureMatches() {
            // "celebrate" lemmatises to "celebrate" — not in {approve, reject, abstain}
            var r = registry.match("protocol:review", "I celebrated this achievement");
            assertThat(r)
                .as("structural match but wrong enum → rejected")
                .isInstanceOf(TemplateMatch.Unmatched.class);
        }
    }

    /**
     * NLP Variable Extraction — how the NLP pipeline extracts different variable types.
     *
     * <p>Two extraction modes, determined by the variable's constraint type:
     *
     * <p><b>Enum variables</b> (e.g. decision: {approve, reject, abstain}): The extracted
     * value is the <em>lemma</em>, not the surface text. If the input says "approved",
     * the extracted value is "approve" — the base form that matches the enum definition.
     * This is what makes NLP useful for protocols: the extracted value is always one of the
     * declared enum values, regardless of the verb tense in the input.
     *
     * <p><b>String variables</b> (e.g. subject, rationale): The extracted value is the
     * <em>surface text</em> — the exact words from the input, preserved verbatim. String
     * variables capture everything between structural anchors (literal words in the template).
     * This means a variable can capture multi-word phrases: "the chief information security
     * officer" or "repeated failures in the deployment pipeline that are blocking three
     * downstream teams".
     *
     * <p><b>Optional segments</b> (e.g. {@code [ because ${rationale}]}): When the input
     * includes the optional part, the variable is extracted. When omitted, the field is
     * simply absent from the result — no error, no empty string.
     */
    @Nested
    class NlpVariableExtraction {

        @BeforeEach void init() { registerProtocol(); }

        @Test
        void enumExtractsLemma_stringExtractsSurface() {
            var r = (TemplateMatch.Matched) registry.match(
                "protocol:review", "I approved this design");
            assertThat(r.fields().get("decision"))
                .as("enum variable: extracted as LEMMA 'approve' not surface 'approved'")
                .isEqualTo("approve");
            assertThat(r.fields().get("subject"))
                .as("string variable: extracted as SURFACE text")
                .contains("design");
        }

        @ParameterizedTest(name = "enum '{0}': \"{1}\"")
        @CsvSource({
            "approve, I approve this design",
            "reject, I reject this proposal",
            "abstain, I abstain this motion"
        })
        void allEnumValuesExtractCorrectly(String expectedLemma, String input) {
            var r = (TemplateMatch.Matched) registry.match("protocol:review", input);
            assertThat(r.fields().get("decision")).isEqualTo(expectedLemma);
        }

        @Test
        void multiWordSubjectCapture() {
            var r = (TemplateMatch.Matched) registry.match("protocol:review",
                "I approve this proposed migration strategy for the legacy database");
            assertThat(r.fields().get("subject"))
                .as("clause captures entire multi-word phrase")
                .contains("proposed")
                .contains("migration")
                .contains("legacy database");
        }

        @Test
        void longRationaleWithTechnicalContent() {
            var r = (TemplateMatch.Matched) registry.match("protocol:review",
                "I approve this design because the separation of concerns is clean and the error handling covers all edge cases we identified in the threat model");
            assertThat(r.fields().get("rationale"))
                .as("captures long clause with technical terminology intact")
                .contains("separation of concerns")
                .contains("threat model");
        }

        @Test
        void optionalSegmentPresentExtractsValue() {
            var r = (TemplateMatch.Matched) registry.match("protocol:review",
                "I approve this design because it is elegant");
            assertThat(r.fields())
                .as("optional rationale present → extracted")
                .containsKey("rationale");
            assertThat(r.fields().get("rationale")).contains("elegant");
        }

        @Test
        void optionalSegmentAbsentNoField() {
            var r = (TemplateMatch.Matched) registry.match("protocol:review",
                "I approve this design");
            assertThat(r.fields())
                .as("optional rationale omitted → no field")
                .doesNotContainKey("rationale");
        }

        @Test
        void escalationMultiWordTargetCapture() {
            var r = (TemplateMatch.Matched) registry.match("protocol:review",
                "Escalating to the chief information security officer");
            assertThat(r.fields().get("target"))
                .as("captures multi-word title as single clause")
                .contains("chief")
                .contains("information")
                .contains("security officer");
        }

        @Test
        void escalationWithDetailedReasonCapture() {
            var r = (TemplateMatch.Matched) registry.match("protocol:review",
                "Escalating to management due to repeated failures in the deployment pipeline that are blocking three downstream teams from releasing");
            assertThat(r.fields().get("reason"))
                .as("long reason clause preserved verbatim")
                .contains("repeated failures")
                .contains("downstream teams");
        }
    }

    /**
     * Mapping to Protocol Actions — how extracted matches drive downstream logic.
     *
     * <p>When the registry matches input, it returns a {@link TemplateMatch.Matched} with:
     * <ul>
     *   <li>{@code namespace} — which protocol this belongs to (e.g. "protocol:review")</li>
     *   <li>{@code templateName} — which action type matched (e.g. "vote" or "escalate")</li>
     *   <li>{@code fields} — the extracted parameters (e.g. {decision: "approve", subject: "architecture"})</li>
     * </ul>
     *
     * <p>A protocol engine uses {@code templateName} to dispatch to the right handler
     * (vote → tally votes, escalate → route to manager) and {@code fields} as the
     * handler's parameters. The key insight: NLP tense normalisation means "I rejected
     * this proposal" drives the same rejection action as "I reject this proposal" —
     * the protocol engine sees {@code decision=reject} in both cases.
     */
    @Nested
    class MappingToProtocolActions {

        @BeforeEach void init() { registerProtocol(); }

        @Test
        void voteApproval_drivesApproveAction() {
            var r = (TemplateMatch.Matched) registry.match("protocol:review",
                "I approve this architecture");
            assertThat(r.namespace()).isEqualTo("protocol:review");
            assertThat(r.templateName()).as("template = action type").isEqualTo("vote");
            assertThat(r.fields().get("decision")).as("action = approve").isEqualTo("approve");
            assertThat(r.fields().get("subject")).as("target = architecture").contains("architecture");
        }

        @Test
        void voteRejection_drivesRejectAction() {
            var r = (TemplateMatch.Matched) registry.match("protocol:review",
                "I reject this proposal");
            assertThat(r.templateName()).isEqualTo("vote");
            assertThat(r.fields().get("decision")).isEqualTo("reject");
        }

        @Test
        void escalation_drivesRoutingWithContext() {
            var r = (TemplateMatch.Matched) registry.match("protocol:review",
                "Escalating to the security team due to unresolved compliance gaps");
            assertThat(r.templateName()).as("escalation action").isEqualTo("escalate");
            assertThat(r.fields().get("target")).as("routing target").contains("security");
            assertThat(r.fields().get("reason")).as("context for routing").contains("compliance");
        }

        @Test
        void nlpTenseVariation_stillDrivesCorrectAction() {
            // Past tense "rejected" → lemma "reject" → same action as present tense
            var r = (TemplateMatch.Matched) registry.match("protocol:review",
                "I rejected this proposal");
            assertThat(r.templateName()).isEqualTo("vote");
            assertThat(r.fields().get("decision"))
                .as("past tense normalises to same action")
                .isEqualTo("reject");
        }

        @Test
        void templateNameDiscriminatesBetweenActions() {
            var vote = (TemplateMatch.Matched) registry.match("protocol:review",
                "I approve this design");
            var escalation = (TemplateMatch.Matched) registry.match("protocol:review",
                "Escalating to Sarah");
            assertThat(vote.templateName())
                .as("vote and escalate are distinct action types")
                .isNotEqualTo(escalation.templateName());
        }
    }

    /**
     * Sentence Variation — proving the system handles many ways of saying the same thing.
     *
     * <p>An LLM might write "I approve", "I approved", "I approves", or "I approve these
     * designs" — all meaning the same action. The registry normalises all variants to the
     * same extracted result: {@code decision=approve}. This is the practical test: can a
     * protocol author define one template and have it match the range of natural language
     * the LLM will actually produce?
     */
    @Nested
    class SentenceVariation {

        @BeforeEach void init() { registerProtocol(); }

        @ParameterizedTest(name = "\"{0}\" → normalised decision={1}")
        @CsvSource({
            "I approve this design,    approve",
            "I approved this design,   approve",
            "I approves this design,   approve",
            "I approve these designs,  approve",
            "I reject this proposal,   reject",
            "I rejected this proposal, reject"
        })
        void allVariantsNormaliseToSameAction(String input, String expectedLemma) {
            var r = registry.match("protocol:review", input);
            assertThat(r)
                .as("'%s' should match", input)
                .isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) r).fields().get("decision"))
                .as("'%s' normalises decision to '%s'", input, expectedLemma)
                .isEqualTo(expectedLemma);
        }

        @Test
        void shortAndLongVersionsMapToSameAction() {
            var shortResult = (TemplateMatch.Matched) registry.match(
                "protocol:review", "I approve this design");
            var longResult = (TemplateMatch.Matched) registry.match(
                "protocol:review",
                "I approve this proposed architecture because the design is thorough and addresses all our security requirements");

            assertThat(shortResult.templateName())
                .isEqualTo(longResult.templateName());
            assertThat(shortResult.fields().get("decision"))
                .isEqualTo(longResult.fields().get("decision"));
        }
    }

    /**
     * Bidirectional Roundtrip — proving extract and generate are inverses.
     *
     * <p>Templates are bidirectional: they parse prose into fields (extract) and produce
     * prose from fields (generate). The roundtrip property means:
     * <ul>
     *   <li>extract("I approve this design") → {decision: approve, subject: design}</li>
     *   <li>generate({decision: approve, subject: design}) → "I approve this design"</li>
     * </ul>
     *
     * <p>Generation uses template slot-filling: the template pattern is the grammatically
     * correct surface form, and variable values are substituted into their positions.
     * Optional segments are included when all their variables have values, omitted otherwise.
     */
    @Nested
    class BidirectionalRoundtrip {

        @BeforeEach void init() { registerProtocol(); }

        @Test
        void extractThenGenerate_simpleVote() {
            String input = "I approve this design";
            var matched = (TemplateMatch.Matched) registry.match("protocol:review", input);
            String generated = registry.generate(
                "protocol:review", matched.templateName(), matched.fields());
            assertThat(generated).isEqualTo(input);
        }

        @Test
        void extractThenGenerate_withOptionalClause() {
            String input = "I reject this proposal because it lacks detail";
            var matched = (TemplateMatch.Matched) registry.match("protocol:review", input);
            String generated = registry.generate(
                "protocol:review", matched.templateName(), matched.fields());
            assertThat(generated).isEqualTo(input);
        }

        @Test
        void generateThenMatch_verifyFieldsPreserved() {
            String generated = registry.generate("protocol:review", "vote",
                Map.of("decision", "reject", "subject", "the proposed API change",
                    "rationale", "it breaks backward compatibility"));
            var matched = (TemplateMatch.Matched) registry.match("protocol:review", generated);
            assertThat(matched.fields()).containsEntry("decision", "reject");
            assertThat(matched.fields().get("subject")).isEqualTo("the proposed API change");
            assertThat(matched.fields().get("rationale")).isEqualTo("it breaks backward compatibility");
        }

        @Test
        void generateOmitsOptionalWhenNotProvided() {
            String generated = registry.generate("protocol:review", "vote",
                Map.of("decision", "approve", "subject", "architecture"));
            assertThat(generated).isEqualTo("I approve this architecture");
        }

        @Test
        void generateIncludesOptionalWhenProvided() {
            String generated = registry.generate("protocol:review", "vote",
                Map.of("decision", "approve", "subject", "architecture",
                    "rationale", "clean separation of concerns"));
            assertThat(generated).isEqualTo(
                "I approve this architecture because clean separation of concerns");
        }
    }

    /**
     * Schema Publication — proving NLP compilation data is discoverable.
     *
     * <p>When templates are registered, the NLP pipeline compiles each pattern into
     * structural elements (LITERAL with POS tag and lemma, CLAUSE for variable positions).
     * This structural data is published as a {@link TemplateSchema} — available via the
     * registry API and exposed via {@code @McpDomain} for LLM prompt construction.
     *
     * <p>The schema tells an LLM: "this protocol expects sentences that look like this,
     * with these variable positions and these allowed values." The LLM conforms to the
     * schema; the platform extracts mechanically.
     */
    @Nested
    class SchemaPublication {

        @BeforeEach void init() { registerProtocol(); }

        @Test
        void schemaAvailableAfterRegistration() {
            var schema = registry.schema("protocol:review", "vote");
            assertThat(schema).isPresent();
            assertThat(schema.get().patterns()).hasSize(1);
            assertThat(schema.get().variables()).containsKeys("decision", "subject", "rationale");
        }

        @Test
        void nlpPopulatesStructuralSummaries() {
            var schema = registry.schema("protocol:review", "vote").orElseThrow();
            assertThat(schema.structuralSummaries())
                .as("NLP compiles structural element data")
                .isNotEmpty();

            var elements = schema.structuralSummaries().get(0).elements();
            assertThat(elements.stream().anyMatch(e -> "LITERAL".equals(e.kind())))
                .as("contains LITERAL elements with POS tags and lemmas")
                .isTrue();
            assertThat(elements.stream().anyMatch(e -> "CLAUSE".equals(e.kind())))
                .as("contains CLAUSE elements for variable positions")
                .isTrue();

            var literals = elements.stream()
                .filter(e -> "LITERAL".equals(e.kind()))
                .toList();
            assertThat(literals).allSatisfy(e -> {
                assertThat(e.lemma()).as("literal has lemma").isNotNull();
                assertThat(e.pos()).as("literal has POS tag").isNotNull();
            });
        }

        @Test
        void allSchemasForNamespace() {
            assertThat(registry.schemas("protocol:review")).hasSize(2);
        }
    }

    /**
     * Rich Protocol — a four-variable template exercising all constraint types together.
     *
     * <p>Template: "Requesting ${action} on ${resource} from ${authority}[ because ${justification}]"
     * <ul>
     *   <li>{@code action} — enum {approval, review, access, exception}</li>
     *   <li>{@code resource} — string (multi-word capture)</li>
     *   <li>{@code authority} — string (multi-word capture)</li>
     *   <li>{@code justification} — optional string (clause capture)</li>
     * </ul>
     *
     * <p>Tests that all four variables extract correctly, that the optional segment
     * works, that invalid enum values are rejected, and that the bidirectional
     * roundtrip preserves all four fields.
     */
    @Nested
    class RichProtocol {

        @BeforeEach
        void init() {
            var request = TemplateDefinition.builder("request")
                .pattern("Requesting ${action} on ${resource} from ${authority}[ because ${justification}]")
                .variable("action", VariableConstraint.enumOf("approval", "review", "access", "exception"))
                .variable("resource", VariableConstraint.string())
                .variable("authority", VariableConstraint.string())
                .variable("justification", VariableConstraint.string().optional())
                .build();
            var templates = new LinkedHashMap<String, TemplateDefinition>();
            templates.put("request", request);
            registry.register(new TemplateNamespace("protocol:governance", templates));
        }

        @Test
        void allFourVariablesExtracted() {
            var r = (TemplateMatch.Matched) registry.match("protocol:governance",
                "Requesting approval on the production deployment from the release manager because the deadline is tomorrow");
            assertThat(r.fields()).containsEntry("action", "approval");
            assertThat(r.fields().get("resource")).contains("production");
            assertThat(r.fields().get("authority")).contains("release");
            assertThat(r.fields().get("justification")).contains("deadline");
        }

        @Test
        void threeVariablesWithoutOptional() {
            var r = (TemplateMatch.Matched) registry.match("protocol:governance",
                "Requesting review on the API changes from the architect");
            assertThat(r.fields()).containsEntry("action", "review");
            assertThat(r.fields()).doesNotContainKey("justification");
        }

        @Test
        void invalidActionRejected() {
            var r = registry.match("protocol:governance",
                "Requesting deletion on the database from the admin");
            assertThat(r).isInstanceOf(TemplateMatch.Unmatched.class);
        }

        @Test
        void bidirectionalWithFourVariables() {
            String input = "Requesting access on the staging environment from operations";
            var matched = (TemplateMatch.Matched) registry.match("protocol:governance", input);
            String generated = registry.generate(
                "protocol:governance", matched.templateName(), matched.fields());
            assertThat(generated).isEqualTo(input);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  MULTI-NAMESPACE — isolation and cross-namespace search
    // ─────────────────────────────────────────────────────────────

    @Nested
    class MultiNamespace {

        @Test
        void crossNamespaceGlobalMatch() {
            registerProtocol();
            var ack = TemplateDefinition.builder("ack")
                .pattern("Acknowledged by ${actor}")
                .variable("actor", VariableConstraint.string())
                .build();
            var templates = new LinkedHashMap<String, TemplateDefinition>();
            templates.put("ack", ack);
            registry.register(new TemplateNamespace("protocol:workflow", templates));

            var r1 = (TemplateMatch.Matched) registry.match("I approve this design");
            assertThat(r1.namespace()).isEqualTo("protocol:review");

            var r2 = (TemplateMatch.Matched) registry.match("Acknowledged by Sarah");
            assertThat(r2.namespace()).isEqualTo("protocol:workflow");
        }

        @Test
        void namespaceScopedMatchDoesNotCross() {
            registerProtocol();
            var r = registry.match("protocol:review", "Acknowledged by Sarah");
            assertThat(r).isInstanceOf(TemplateMatch.Unmatched.class);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  FALSE POSITIVE REJECTION
    // ─────────────────────────────────────────────────────────────

    @Nested
    class FalsePositiveRejection {

        @BeforeEach void init() { registerProtocol(); }

        @ParameterizedTest(name = "rejects: \"{0}\"")
        @ValueSource(strings = {
            "Let me think about this for a while",
            "Great work on the implementation",
            "Can we schedule a meeting to discuss?",
            "The deployment pipeline is running smoothly"
        })
        void conversationalInputRejects(String input) {
            var r = registry.match("protocol:review", input);
            assertThat(r).isInstanceOf(TemplateMatch.Unmatched.class);
        }
    }

    /**
     * The Paraphrase Cliff — proving the boundary between inflection and paraphrase.
     *
     * <p>The NLP tier handles <b>inflection</b> — grammatical variation of the same words:
     * "approved" → "approve", "these" → "this", "designs" → "design". All of these preserve
     * the template's structural pattern; the words are the same, just inflected differently.
     *
     * <p>The NLP tier does NOT handle <b>paraphrase</b> — semantically equivalent sentences
     * with different words or structure. "I'm in favour of this design" means the same thing
     * as "I approve this design", but the words and structure are completely different. The
     * structural matcher has no way to know that "in favour of" is a synonym for "approve".
     *
     * <p>This is a deliberate boundary, not a bug. Schema publication mitigates it: the
     * published template schema tells LLMs exactly what sentence shapes the protocol expects.
     * A well-prompted agent conforms to the schema. But agents drift from schemas in
     * multi-turn conversations — this test documents what happens when they do.
     */
    @Nested
    class ParaphraseCliff {

        @BeforeEach void init() { registerProtocol(); }

        @Test
        void inflectionMatches_paraphraseDoesNot() {
            // Inflection: same structure, different verb form → NLP handles it
            var inflected = registry.match("protocol:review", "I approved this design");
            assertThat(inflected)
                .as("inflection ('approved') is handled — same word, different form")
                .isInstanceOf(TemplateMatch.Matched.class);
            assertThat(((TemplateMatch.Matched) inflected).fields().get("decision"))
                .isEqualTo("approve");

            // Paraphrase: same meaning, different words → NOT handled
            var paraphrased = registry.match("protocol:review",
                "I'm in favour of this design");
            assertThat(paraphrased)
                .as("paraphrase ('in favour of') is NOT handled — different words entirely")
                .isInstanceOf(TemplateMatch.Unmatched.class);
        }

        @ParameterizedTest(name = "paraphrase unmatched: \"{0}\"")
        @ValueSource(strings = {
            "I'm in favour of this design",
            "This design has my approval",
            "I give this design the green light",
            "I support this proposal fully",
            "Thumbs up on this architecture",
            "I'm on board with this approach"
        })
        void semanticEquivalentsDoNotMatch(String input) {
            var r = registry.match("protocol:review", input);
            assertThat(r)
                .as("'%s' is semantically equivalent to 'I approve this design' "
                    + "but structurally different — the NLP handles inflection, not paraphrase", input)
                .isInstanceOf(TemplateMatch.Unmatched.class);
        }

        @Test
        void schemaPublicationMitigatesParaphrase() {
            // The schema tells the LLM exactly what shapes to produce
            var schema = registry.schema("protocol:review", "vote");
            assertThat(schema).isPresent();
            assertThat(schema.get().patterns())
                .as("schema publishes the expected sentence pattern — "
                    + "LLMs that conform to the schema won't paraphrase")
                .containsExactly("I ${decision} this ${subject}[ because ${rationale}]");
            assertThat(schema.get().variables())
                .as("schema publishes allowed values — LLMs know the vocabulary")
                .containsKey("decision");
        }
    }

    /**
     * End-to-End: Prose → Match → Extract → Function Call / Event.
     *
     * <p>This is the full pipeline that protocols use. An LLM produces a sentence.
     * The registry matches it, extracts typed fields, and those fields drive a
     * concrete function call or event emission. The test simulates this with simple
     * functional interfaces standing in for the protocol engine.
     *
     * <p>The pipeline:
     * <ol>
     *   <li>LLM writes: "I approved this architecture because it handles failover"</li>
     *   <li>Registry matches → templateName="vote", decision="approve", subject="architecture", rationale="it handles failover"</li>
     *   <li>Dispatcher routes on templateName → calls {@code recordVote(decision, subject, rationale)}</li>
     *   <li>Vote handler emits a {@code VoteRecordedEvent} with the extracted fields</li>
     * </ol>
     *
     * <p>The key: steps 3 and 4 work identically whether the input was
     * "I approve", "I approved", or "I approves" — NLP normalisation means
     * the dispatcher always sees {@code decision=approve}.
     */
    @Nested
    class EndToEndFunctionAndEvent {

        record VoteRecordedEvent(String decision, String subject, String rationale) {}
        record EscalationEvent(String target, String reason) {}

        @FunctionalInterface
        interface VoteHandler {
            VoteRecordedEvent recordVote(String decision, String subject, String rationale);
        }

        @FunctionalInterface
        interface EscalationHandler {
            EscalationEvent escalate(String target, String reason);
        }

        @BeforeEach void init() { registerProtocol(); }

        @Test
        void proseToFunctionCall_voteApproval() {
            // Simulate a protocol handler that processes votes
            VoteHandler handler = VoteRecordedEvent::new;

            // LLM writes prose with past tense — NLP normalises
            var match = registry.match("protocol:review",
                "I approved this architecture because it handles failover");
            assertThat(match).isInstanceOf(TemplateMatch.Matched.class);
            var m = (TemplateMatch.Matched) match;

            // Dispatch on templateName → call the right handler with extracted fields
            assertThat(m.templateName()).isEqualTo("vote");
            var event = handler.recordVote(
                m.fields().get("decision"),
                m.fields().get("subject"),
                m.fields().get("rationale"));

            // The event carries normalised, structured data
            assertThat(event.decision())
                .as("function receives normalised lemma, not surface 'approved'")
                .isEqualTo("approve");
            assertThat(event.subject()).contains("architecture");
            assertThat(event.rationale()).contains("failover");
        }

        @Test
        void proseToFunctionCall_escalation() {
            EscalationHandler handler = EscalationEvent::new;

            var match = registry.match("protocol:review",
                "Escalating to the security team due to unresolved compliance gaps");
            assertThat(match).isInstanceOf(TemplateMatch.Matched.class);
            var m = (TemplateMatch.Matched) match;

            assertThat(m.templateName()).isEqualTo("escalate");
            var event = handler.escalate(
                m.fields().get("target"),
                m.fields().get("reason"));

            assertThat(event.target()).contains("security");
            assertThat(event.reason()).contains("compliance");
        }

        @Test
        void dispatchByTemplateName_routesToCorrectHandler() {
            // Multiple handlers keyed by templateName — the protocol dispatch pattern
            var votes = new java.util.ArrayList<VoteRecordedEvent>();
            var escalations = new java.util.ArrayList<EscalationEvent>();

            String[] inputs = {
                "I approved this design",
                "Escalating to the manager due to blocked pipeline",
                "I rejected this proposal because it is incomplete"
            };

            for (String input : inputs) {
                var match = registry.match("protocol:review", input);
                if (match instanceof TemplateMatch.Matched m) {
                    switch (m.templateName()) {
                        case "vote" -> votes.add(new VoteRecordedEvent(
                            m.fields().get("decision"),
                            m.fields().get("subject"),
                            m.fields().get("rationale")));
                        case "escalate" -> escalations.add(new EscalationEvent(
                            m.fields().get("target"),
                            m.fields().get("reason")));
                    }
                }
            }

            assertThat(votes).hasSize(2);
            assertThat(votes.get(0).decision())
                .as("past tense 'approved' normalised to 'approve'")
                .isEqualTo("approve");
            assertThat(votes.get(1).decision())
                .as("past tense 'rejected' normalised to 'reject'")
                .isEqualTo("reject");
            assertThat(votes.get(1).rationale()).contains("incomplete");

            assertThat(escalations).hasSize(1);
            assertThat(escalations.get(0).target()).contains("manager");
            assertThat(escalations.get(0).reason()).contains("blocked");
        }

        @Test
        void nlpVariationsAllDriveSameFunctionCall() {
            // Three different surface forms — all produce the same function call
            VoteHandler handler = VoteRecordedEvent::new;

            String[] variations = {
                "I approve this design",
                "I approved this design",
                "I approves this design"
            };

            for (String input : variations) {
                var m = (TemplateMatch.Matched) registry.match("protocol:review", input);
                var event = handler.recordVote(
                    m.fields().get("decision"),
                    m.fields().get("subject"),
                    m.fields().get("rationale"));
                assertThat(event.decision())
                    .as("'%s' → function call with decision=approve", input)
                    .isEqualTo("approve");
            }
        }
    }

    /**
     * Pronouns and Named Agents — different speakers with the same template.
     *
     * <p>Template: "On behalf of ${agent} I ${decision} this ${subject}"
     *
     * <p>Templates need literal anchors between adjacent variables — the structural
     * matcher uses literals as boundaries for CLAUSE capture. "On behalf of" anchors
     * before ${agent}, "I" anchors between ${agent} and ${decision}.
     *
     * <p>This models the protocol pattern where an agent acts on behalf of a named
     * entity (a person, team, or committee). The verb inflection is handled by NLP
     * regardless of who the agent is — "approved", "approve", "approves" all normalise
     * to the same lemma.
     */
    @Nested
    class PronounHandling {

        @BeforeEach
        void init() {
            var review = TemplateDefinition.builder("review")
                .pattern("On behalf of ${agent} I ${decision} this ${subject}")
                .variable("agent", VariableConstraint.string())
                .variable("decision", VariableConstraint.enumOf("approve", "reject"))
                .variable("subject", VariableConstraint.string())
                .build();
            var templates = new LinkedHashMap<String, TemplateDefinition>();
            templates.put("review", review);
            registry.register(new TemplateNamespace("protocol:agent-review", templates));
        }

        @ParameterizedTest(name = "\"{0}\" → agent contains \"{1}\", decision={2}")
        @CsvSource({
            "On behalf of Sarah I approve this design,         Sarah,         approve",
            "On behalf of Mark I reject this proposal,         Mark,          reject",
            "On behalf of the committee I approve this budget, the committee, approve"
        })
        void namedAgentsExtracted(String input, String expectedAgent,
                                  String expectedDecision) {
            var r = registry.match("protocol:agent-review", input);
            assertThat(r)
                .as("'%s' should match", input)
                .isInstanceOf(TemplateMatch.Matched.class);
            var m = (TemplateMatch.Matched) r;
            assertThat(m.fields().get("agent"))
                .as("agent captured from '%s'", input)
                .contains(expectedAgent.trim());
            assertThat(m.fields().get("decision"))
                .as("verb normalised from '%s'", input)
                .isEqualTo(expectedDecision.trim());
        }

        @Test
        void pastTenseWithNamedAgent() {
            var r = registry.match("protocol:agent-review",
                "On behalf of Sarah I approved this architecture");
            assertThat(r).isInstanceOf(TemplateMatch.Matched.class);
            var m = (TemplateMatch.Matched) r;
            assertThat(m.fields().get("agent")).contains("Sarah");
            assertThat(m.fields().get("decision"))
                .as("'approved' normalises to 'approve' regardless of agent")
                .isEqualTo("approve");
        }

        @Test
        void multiWordAgentCapture() {
            var r = registry.match("protocol:agent-review",
                "On behalf of the security review board I reject this deployment plan");
            assertThat(r).isInstanceOf(TemplateMatch.Matched.class);
            var m = (TemplateMatch.Matched) r;
            assertThat(m.fields().get("agent"))
                .as("multi-word agent captured")
                .contains("security review board");
            assertThat(m.fields().get("decision")).isEqualTo("reject");
        }
    }

    /**
     * Singular and Plural — testing that templates handle count variation.
     *
     * <p>When an LLM refers to one item or multiple items, several things change:
     * <ul>
     *   <li>Determiners: "this" → "these", "a" → "some"</li>
     *   <li>Nouns: "design" → "designs"</li>
     *   <li>Verbs may change agreement: "it needs" → "they need"</li>
     * </ul>
     *
     * <p>The NLP pipeline handles this through lemmatisation: "these" and "this" share
     * a common lemma, "designs" lemmatises to "design". The structural matcher compares
     * lemmas, so plural forms match the same template as singular forms.
     *
     * <p>The extracted value preserves the surface form — the caller sees "designs"
     * not "design" — so they know whether the input was singular or plural.
     */
    @Nested
    class SingularAndPlural {

        @BeforeEach void init() { registerProtocol(); }

        @ParameterizedTest(name = "\"{0}\" → subject contains \"{1}\"")
        @CsvSource({
            "I approve this design,              design",
            "I approve these designs,             designs",
            "I approve this proposal,             proposal",
            "I approve these proposals,           proposals"
        })
        void singularAndPluralBothMatch(String input, String expectedSubject) {
            var r = registry.match("protocol:review", input);
            assertThat(r)
                .as("'%s' should match", input)
                .isInstanceOf(TemplateMatch.Matched.class);
            var m = (TemplateMatch.Matched) r;
            assertThat(m.fields().get("decision")).isEqualTo("approve");
            assertThat(m.fields().get("subject"))
                .as("surface form preserved: '%s'", expectedSubject.trim())
                .contains(expectedSubject.trim());
        }

        @Test
        void pluralSubjectPreservesSurfaceForm() {
            var r = (TemplateMatch.Matched) registry.match("protocol:review",
                "I approve these architectural proposals");
            assertThat(r.fields().get("subject"))
                .as("plural surface form preserved, not lemmatised to singular")
                .contains("proposals");
        }

        @Test
        void generationDoesNotAdaptDeterminerToPlural() {
            // Known limitation: slot-filling generates "this designs" (wrong)
            // because the template has "this" as a literal and generation doesn't
            // adapt grammar. Future morphological generation (#962) would produce
            // "these designs" when the subject is plural.
            String generated = registry.generate("protocol:review", "vote",
                Map.of("decision", "approve", "subject", "designs"));
            assertThat(generated)
                .as("slot-filling keeps the template's literal 'this' — does NOT adapt to 'these' for plural. "
                    + "This is the morphological generation gap: matching handles plurals, generation doesn't yet.")
                .isEqualTo("I approve this designs");
        }
    }

    /**
     * Comma-Separated Lists — capturing enumerated items in a single variable.
     *
     * <p>When an LLM lists items — "the API, the database, and the deployment pipeline" —
     * the CLAUSE capture mechanism grabs the entire phrase between structural anchors.
     * The registry doesn't parse the list into individual items (that's a consumer concern),
     * but it preserves the full text including commas and conjunctions.
     *
     * <p>This is important for protocol justifications and descriptions where LLMs
     * naturally enumerate: "because it breaks the API, the security model, and the
     * backwards compatibility guarantee".
     */
    @Nested
    class CommaSeparatedListCapture {

        @BeforeEach void init() { registerProtocol(); }

        @Test
        void capturesCommaSeparatedSubject() {
            var r = (TemplateMatch.Matched) registry.match("protocol:review",
                "I approve this API, database, and deployment pipeline");
            assertThat(r.fields().get("subject"))
                .as("comma-separated list captured as single value")
                .contains("API")
                .contains("database")
                .contains("deployment pipeline");
        }

        @Test
        void capturesCommaSeparatedRationale() {
            var r = (TemplateMatch.Matched) registry.match("protocol:review",
                "I reject this proposal because it breaks the API, the security model, and the backwards compatibility guarantee");
            assertThat(r.fields().get("rationale"))
                .as("enumerated reasons captured intact")
                .contains("API")
                .contains("security model")
                .contains("backwards compatibility");
        }

        @Test
        void capturesEscalationWithMultipleReasons() {
            var r = (TemplateMatch.Matched) registry.match("protocol:review",
                "Escalating to management due to failed builds, missed deadlines, and unresolved security findings");
            assertThat(r.fields().get("reason"))
                .as("comma-separated reasons preserved")
                .contains("failed builds")
                .contains("missed deadlines")
                .contains("security findings");
        }

        @Test
        void capturesListWithNumberedItems() {
            var r = (TemplateMatch.Matched) registry.match("protocol:review",
                "I reject this proposal because 1) no error handling, 2) no tests, 3) no documentation");
            assertThat(r.fields().get("rationale"))
                .as("numbered list captured verbatim")
                .contains("1)")
                .contains("2)")
                .contains("3)");
        }
    }
}
