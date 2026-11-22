package io.casehub.yaml.core.resolver;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateExtractorTest {

    @Test
    void extractsSingleVariable() {
        var extractor = TemplateExtractor.compile("Hello ${name}");
        Optional<Map<String, String>> result = extractor.extract("Hello world");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(Map.entry("name", "world"));
    }

    @Test
    void extractsMultipleVariables() {
        var extractor = TemplateExtractor.compile("I ${decision} this design because ${rationale}");
        Optional<Map<String, String>> result = extractor.extract("I approve this design because the error handling is solid");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "decision", "approve",
                "rationale", "the error handling is solid"
        ));
    }

    @Test
    void lazyMatchingBetweenVariablesGreedyForLast() {
        var extractor = TemplateExtractor.compile("${first} and ${second} and ${third}");
        Optional<Map<String, String>> result = extractor.extract("a and b and c and d");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "first", "a",
                "second", "b",
                "third", "c and d"
        ));
    }

    @Test
    void returnsEmptyWhenNoMatch() {
        var extractor = TemplateExtractor.compile("I ${decision} this design");
        Optional<Map<String, String>> result = extractor.extract("completely different text");
        assertThat(result).isEmpty();
    }

    @Test
    void literalSegmentsAreRegexQuoted() {
        var extractor = TemplateExtractor.compile("price is $${amount} (USD)");
        Optional<Map<String, String>> result = extractor.extract("price is $42.50 (USD)");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(Map.entry("amount", "42.50"));
    }

    @Test
    void templateWithNoVariablesMatchesLiteralExactly() {
        var extractor = TemplateExtractor.compile("hello world");
        assertThat(extractor.extract("hello world")).isPresent();
        assertThat(extractor.extract("hello world").get()).isEmpty();
        assertThat(extractor.extract("hello universe")).isEmpty();
    }

    @Test
    void variableAtStartOfTemplate() {
        var extractor = TemplateExtractor.compile("${greeting}, world");
        Optional<Map<String, String>> result = extractor.extract("hi there, world");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(Map.entry("greeting", "hi there"));
    }

    @Test
    void variableAtEndOfTemplate() {
        var extractor = TemplateExtractor.compile("hello ${name}");
        Optional<Map<String, String>> result = extractor.extract("hello Mark Proctor");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(Map.entry("name", "Mark Proctor"));
    }

    @Test
    void adjacentVariables() {
        var extractor = TemplateExtractor.compile("${a}${b}");
        Optional<Map<String, String>> result = extractor.extract("xy");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactlyInAnyOrderEntriesOf(Map.of("a", "x", "b", "y"));
    }

    @Test
    void variablesRequireAtLeastOneCharacter() {
        var extractor = TemplateExtractor.compile("start ${a} end");
        assertThat(extractor.extract("start  end")).isEmpty();
    }

    @Test
    void compileRejectsNullTemplate() {
        assertThatThrownBy(() -> TemplateExtractor.compile(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void extractRejectsNullInput() {
        var extractor = TemplateExtractor.compile("${x}");
        assertThatThrownBy(() -> extractor.extract(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void compileRejectsDuplicateVariableNames() {
        assertThatThrownBy(() -> TemplateExtractor.compile("${a} and ${a}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void compileRejectsInvalidVariableNames() {
        assertThatThrownBy(() -> TemplateExtractor.compile("${1x}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid variable name");
        assertThatThrownBy(() -> TemplateExtractor.compile("${.dotstart}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid variable name");
        assertThatThrownBy(() -> TemplateExtractor.compile("${my var}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid variable name");
    }

    @Test
    void emptyTemplateMatchesEmptyInput() {
        var extractor = TemplateExtractor.compile("");
        assertThat(extractor.extract("")).isPresent();
        assertThat(extractor.extract("")).get().satisfies(m -> assertThat(m).isEmpty());
        assertThat(extractor.extract("non-empty")).isEmpty();
    }

    @Test
    void emptyInputDoesNotMatchVariableTemplate() {
        var extractor = TemplateExtractor.compile("${x}");
        assertThat(extractor.extract("")).isEmpty();
    }

    @Test
    void extractedMapIsUnmodifiable() {
        var                 extractor = TemplateExtractor.compile("hello ${name}");
        Map<String, String> result    = extractor.extract("hello world").orElseThrow();
        assertThatThrownBy(() -> result.put("name", "other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }


    @Test
    void patternAccessor() {
        var extractor = TemplateExtractor.compile("hello ${name}");
        assertThat(extractor.pattern().pattern()).contains("name");
    }

    @Test
    void variableNamesAccessor() {
        var extractor = TemplateExtractor.compile("I ${decision} because ${rationale}");
        assertThat(extractor.variableNames()).containsExactly("decision", "rationale");
    }

    @Test
    void templateAccessor() {
        var extractor = TemplateExtractor.compile("hello ${name}");
        assertThat(extractor.template()).isEqualTo("hello ${name}");
    }

    // --- Optional segments ---

    @Test
    void optionalSegmentMatchesWhenPresent() {
        var extractor = TemplateExtractor.compile("I ${decision} this design[ because ${rationale}]");
        var result = extractor.extract("I approve this design because the error handling is solid");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "decision", "approve",
                "rationale", "the error handling is solid"
        ));
    }

    @Test
    void optionalSegmentMatchesWhenAbsent() {
        var extractor = TemplateExtractor.compile("I ${decision} this design[ because ${rationale}]");
        var result = extractor.extract("I approve this design");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(Map.entry("decision", "approve"));
    }

    @Test
    void optionalSegmentAbsentVariablesNotInMap() {
        var extractor = TemplateExtractor.compile("I ${decision} this design[ because ${rationale}]");
        var result = extractor.extract("I approve this design");
        assertThat(result).isPresent();
        assertThat(result.get()).doesNotContainKey("rationale");
    }

    @Test
    void optionalSegmentWithOnlyLiteralText() {
        var extractor = TemplateExtractor.compile("hello${name}[ world]");
        assertThat(extractor.extract("helloMark world")).isPresent();
        assertThat(extractor.extract("helloMark world").get()).containsExactly(Map.entry("name", "Mark"));
        assertThat(extractor.extract("helloMark")).isPresent();
        assertThat(extractor.extract("helloMark").get()).containsExactly(Map.entry("name", "Mark"));
    }

    @Test
    void optionalSegmentAtStart() {
        var extractor = TemplateExtractor.compile("[${greeting} ]hello");
        var withGreeting = extractor.extract("hi hello");
        assertThat(withGreeting).isPresent();
        assertThat(withGreeting.get()).containsExactly(Map.entry("greeting", "hi"));

        var withoutGreeting = extractor.extract("hello");
        assertThat(withoutGreeting).isPresent();
        assertThat(withoutGreeting.get()).isEmpty();
    }

    @Test
    void multipleOptionalSegments() {
        var extractor = TemplateExtractor.compile("${action}[ by ${actor}][ at ${time}]");
        var both = extractor.extract("deploy by admin at noon");
        assertThat(both).isPresent();
        assertThat(both.get()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "action", "deploy",
                "actor", "admin",
                "time", "noon"
        ));

        var actorOnly = extractor.extract("deploy by admin");
        assertThat(actorOnly).isPresent();
        assertThat(actorOnly.get()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "action", "deploy",
                "actor", "admin"
        ));

        var neitherOptional = extractor.extract("deploy");
        assertThat(neitherOptional).isPresent();
        assertThat(neitherOptional.get()).containsExactly(Map.entry("action", "deploy"));
    }

    @Test
    void optionalSegmentVariableNamesIncludesOptionalVars() {
        var extractor = TemplateExtractor.compile("I ${decision}[ because ${rationale}]");
        assertThat(extractor.variableNames()).containsExactly("decision", "rationale");
    }

    @Test
    void optionalSegmentWithMultipleVariables() {
        var extractor = TemplateExtractor.compile("${action}[ from ${source} to ${target}]");
        var withOptional = extractor.extract("copy from A to B");
        assertThat(withOptional).isPresent();
        assertThat(withOptional.get()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "action", "copy",
                "source", "A",
                "target", "B"
        ));

        var withoutOptional = extractor.extract("copy");
        assertThat(withoutOptional).isPresent();
        assertThat(withoutOptional.get()).containsExactly(Map.entry("action", "copy"));
    }

    @Test
    void compileRejectsUnclosedBracket() {
        assertThatThrownBy(() -> TemplateExtractor.compile("hello[ world"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unclosed optional segment");
    }

    @Test
    void compileRejectsUnmatchedClosingBracket() {
        assertThatThrownBy(() -> TemplateExtractor.compile("hello] world"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unexpected ']'");
    }

    @Test
    void compileRejectsNestedBrackets() {
        assertThatThrownBy(() -> TemplateExtractor.compile("hello[ world[ nested]]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nested optional segments");
    }

    @Test
    void escapedBracketsTreatedAsLiterals() {
        var extractor = TemplateExtractor.compile("array\\[${index}\\]");
        var result = extractor.extract("array[42]");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(Map.entry("index", "42"));
    }

    @Test
    void optionalSegmentPreservesExistingBehavior() {
        var extractor = TemplateExtractor.compile("I ${decision} this design because ${rationale}");
        var result = extractor.extract("I approve this design because the error handling is solid");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "decision", "approve",
                "rationale", "the error handling is solid"
        ));
    }

    // --- Dotted and hyphenated variable names ---

    @Test
    void dottedVariableNameExtractsCorrectly() {
        var extractor = TemplateExtractor.compile("user ${context.user} logged in");
        var result = extractor.extract("user admin logged in");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(Map.entry("context.user", "admin"));
    }

    @Test
    void hyphenatedVariableNameExtractsCorrectly() {
        var extractor = TemplateExtractor.compile("status is ${my-status}");
        var result = extractor.extract("status is active");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(Map.entry("my-status", "active"));
    }

    @Test
    void multipleDottedVariables() {
        var extractor = TemplateExtractor.compile("${context.user} did ${context.action}");
        var result = extractor.extract("admin did deploy");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "context.user", "admin",
                "context.action", "deploy"
        ));
    }

    @Test
    void dottedVariableInOptionalSegment() {
        var extractor = TemplateExtractor.compile("${action}[ by ${context.actor}]");
        var withActor = extractor.extract("deploy by admin");
        assertThat(withActor).isPresent();
        assertThat(withActor.get()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "action", "deploy",
                "context.actor", "admin"
        ));

        var withoutActor = extractor.extract("deploy");
        assertThat(withoutActor).isPresent();
        assertThat(withoutActor.get()).containsExactly(Map.entry("action", "deploy"));
    }

    @Test
    void dottedVariableNamesInVariableNamesAccessor() {
        var extractor = TemplateExtractor.compile("${context.user} did ${action}");
        assertThat(extractor.variableNames()).containsExactly("context.user", "action");
    }

    @Test
    void deeplyDottedVariableName() {
        var extractor = TemplateExtractor.compile("value is ${a.b.c.d}");
        var result = extractor.extract("value is 42");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(Map.entry("a.b.c.d", "42"));
    }

    @Test
    void mixedDottedHyphenatedAndSimpleVariables() {
        var extractor = TemplateExtractor.compile("${user} at ${org.name} with ${access-level}");
        var result = extractor.extract("alice at acme with admin");
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "user", "alice",
                "org.name", "acme",
                "access-level", "admin"
        ));
    }

    @Test
    void duplicateDottedVariableNameRejected() {
        assertThatThrownBy(() -> TemplateExtractor.compile("${a.b} and ${a.b}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    // --- Trailing dots and hyphens ---

    @Test
    void compileRejectsTrailingDot() {
        assertThatThrownBy(() -> TemplateExtractor.compile("${abc.}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid variable name");
    }

    @Test
    void compileRejectsTrailingHyphen() {
        assertThatThrownBy(() -> TemplateExtractor.compile("${abc-}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid variable name");
    }

    @Test
    void compileRejectsConsecutiveDots() {
        assertThatThrownBy(() -> TemplateExtractor.compile("${a..b}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid variable name");
    }

    @Test
    void compileRejectsConsecutiveHyphens() {
        assertThatThrownBy(() -> TemplateExtractor.compile("${a--b}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid variable name");
    }

    @Test
    void compileRejectsConsecutiveDotHyphen() {
        assertThatThrownBy(() -> TemplateExtractor.compile("${a.-b}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid variable name");
    }

    @Test
    void compileRejectsConsecutiveHyphenDot() {
        assertThatThrownBy(() -> TemplateExtractor.compile("${a-.b}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid variable name");
    }
}
