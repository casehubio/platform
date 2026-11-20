package io.casehub.yaml.step.plugin;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MatcherRegistryTest {

    @Nested
    class StructuralMatching {

        @Test
        void literalValuesMatchExactly() {
            var subject = Map.<String, Object>of(
                    "accountId", "acc-100",
                    "currency", "GBP",
                    "amount", 3245.67);

            var expected = Map.<String, Object>of(
                    "accountId", "acc-100",
                    "currency", "GBP",
                    "amount", 3245.67);

            var matcher = MatcherRegistry.resolve("matches").orElseThrow();
            MatchResult result = matcher.apply(subject, expected);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void literalValueMismatchReportsField() {
            var subject = Map.<String, Object>of(
                    "currency", "USD");

            var expected = Map.<String, Object>of(
                    "currency", "GBP");

            var matcher = MatcherRegistry.resolve("matches").orElseThrow();
            MatchResult result = matcher.apply(subject, expected);

            assertThat(result.passed()).isFalse();
            assertThat(result.description()).contains("currency");
            assertThat(result.description()).contains("GBP");
        }

        @Test
        void constraintExpressionUsesExistingMatchers() {
            var subject = Map.<String, Object>of(
                    "amount", 3500.0,
                    "currency", "GBP");

            var expected = Map.of(
                    "amount", Map.of("greaterThan", 3000.0, "lessThan", 4000.0),
                    "currency", Map.of("not-null", true));

            var         matcher = MatcherRegistry.resolve("matches").orElseThrow();
            MatchResult result  = matcher.apply(subject, expected);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void constraintExpressionFailureReportsAll() {
            var subject = Map.<String, Object>of(
                    "amount", 5000.0,
                    "name", "");

            var expected = Map.of(
                    "amount", Map.of("lessThan", 4000.0),
                    "name", Map.of("not-empty", true));

            var         matcher = MatcherRegistry.resolve("matches").orElseThrow();
            MatchResult result  = matcher.apply(subject, expected);

            assertThat(result.passed()).isFalse();
            assertThat(result.description()).contains("amount");
            assertThat(result.description()).contains("name");
        }

        @Test
        void mixedLiteralAndConstraint() {
            var subject = Map.<String, Object>of(
                    "accountId", "acc-100",
                    "amount", 3500.0);

            var expected = Map.of(
                    "accountId", (Object) "acc-100",
                    "amount", Map.of("greaterThan", 3000.0));

            var         matcher = MatcherRegistry.resolve("matches").orElseThrow();
            MatchResult result  = matcher.apply(subject, expected);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void nonMapSubjectFails() {
            var matcher = MatcherRegistry.resolve("matches").orElseThrow();
            MatchResult result = matcher.apply("not-a-map", Map.of("key", "value"));

            assertThat(result.passed()).isFalse();
            assertThat(result.description()).contains("structural match requires a map subject");
        }

        @Test
        void nullSubjectFails() {
            var matcher = MatcherRegistry.resolve("matches").orElseThrow();
            MatchResult result = matcher.apply(null, Map.of("key", "value"));

            assertThat(result.passed()).isFalse();
            assertThat(result.description()).contains("subject is null");
        }

        @Test
        void missingFieldInSubject() {
            var subject = Map.<String, Object>of("currency", "GBP");

            var expected = Map.<String, Object>of(
                    "currency", "GBP",
                    "missing", "value");

            var matcher = MatcherRegistry.resolve("matches").orElseThrow();
            MatchResult result = matcher.apply(subject, expected);

            assertThat(result.passed()).isFalse();
            assertThat(result.description()).contains("missing");
        }

        @Test
        void regexStillWorksWithString() {
            var matcher = MatcherRegistry.resolve("matches").orElseThrow();

            assertThat(matcher.apply("GBP", "^[A-Z]{3}$").passed()).isTrue();
            assertThat(matcher.apply("gbp", "^[A-Z]{3}$").passed()).isFalse();
        }

        @Test
        void unknownMatcherInConstraintReportsFailure() {
            var subject = Map.<String, Object>of("x", 1);
            var expected = Map.of("x", Map.of("nonexistent", 1));

            var matcher = MatcherRegistry.resolve("matches").orElseThrow();
            MatchResult result = matcher.apply(subject, expected);

            assertThat(result.passed()).isFalse();
            assertThat(result.description()).contains("unknown matcher 'nonexistent'");
        }

        @Test
        void stringEqualityViaCasting() {
            var subject = Map.<String, Object>of("count", 42);
            var expected = Map.<String, Object>of("count", "42");

            var matcher = MatcherRegistry.resolve("matches").orElseThrow();
            MatchResult result = matcher.apply(subject, expected);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void emptyExpectedMapPasses() {
            var subject  = Map.<String, Object>of("currency", "GBP");
            var expected = Map.<String, Object>of();

            var         matcher = MatcherRegistry.resolve("matches").orElseThrow();
            MatchResult result  = matcher.apply(subject, expected);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void extraFieldsInSubjectPass() {
            var subject = Map.<String, Object>of(
                    "currency", "GBP",
                    "amount", 100.0,
                    "extra", "ignored");

            var expected = Map.<String, Object>of("currency", "GBP");

            var         matcher = MatcherRegistry.resolve("matches").orElseThrow();
            MatchResult result  = matcher.apply(subject, expected);

            assertThat(result.passed()).isTrue();
        }

        @Test
        void nullFieldInSubjectHandled() {
            var subject = new java.util.HashMap<String, Object>();
            subject.put("name", null);

            var expected = Map.<String, Object>of("name", "expected");

            var         matcher = MatcherRegistry.resolve("matches").orElseThrow();
            MatchResult result  = matcher.apply(subject, expected);

            assertThat(result.passed()).isFalse();
            assertThat(result.description()).contains("name");
        }

    }
}
