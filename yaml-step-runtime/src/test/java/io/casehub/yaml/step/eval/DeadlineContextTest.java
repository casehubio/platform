package io.casehub.yaml.step.eval;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeadlineContext is an immutable value type that tracks absolute deadlines
 * and composes nested timeouts via Math.min. These tests verify the core
 * semantics: no-deadline passthrough, single deadline tracking, and nested
 * composition where the tighter deadline always wins.
 */
class DeadlineContextTest {

    // ── No-Deadline Baseline ──────────────────────────────────────

    @Nested
    class NoDeadlineTests {

        @Test
        void none_hasNoDeadline() {
            assertThat(DeadlineContext.NONE.hasDeadline()).isFalse();
        }

        @Test
        void none_remainingTimeIsEmpty() {
            assertThat(DeadlineContext.NONE.remainingTime()).isEmpty();
        }

        @Test
        void none_isNotExpired() {
            assertThat(DeadlineContext.NONE.isExpired()).isFalse();
        }
    }

    // ── Single Deadline ───────────────────────────────────────────

    @Nested
    class SingleDeadlineTests {

        @Test
        void withTimeout_createsDeadline() {
            var ctx = DeadlineContext.NONE.withTimeout(Duration.ofSeconds(5));
            assertThat(ctx.hasDeadline()).isTrue();
        }

        @Test
        void withTimeout_remainingTimePresent() {
            var ctx = DeadlineContext.NONE.withTimeout(Duration.ofSeconds(5));
            assertThat(ctx.remainingTime()).isPresent();
            assertThat(ctx.remainingTime().get().toMillis()).isLessThanOrEqualTo(5000);
            assertThat(ctx.remainingTime().get().toMillis()).isGreaterThan(0);
        }

        @Test
        void withTimeout_notImmediatelyExpired() {
            var ctx = DeadlineContext.NONE.withTimeout(Duration.ofSeconds(5));
            assertThat(ctx.isExpired()).isFalse();
        }

        @Test
        void withTimeout_zeroOrNegative_immediatelyExpiredOrZeroRemaining() {
            var ctx = DeadlineContext.NONE.withTimeout(Duration.ZERO);
            assertThat(ctx.hasDeadline()).isTrue();
            assertThat(ctx.remainingTime()).isPresent();
            assertThat(ctx.remainingTime().get().toMillis()).isEqualTo(0);
        }

        @Test
        void withTimeout_doesNotMutateOriginal() {
            var original = DeadlineContext.NONE;
            original.withTimeout(Duration.ofSeconds(1));
            assertThat(original.hasDeadline()).isFalse();
        }
    }

    // ── Nested Composition (min semantics) ────────────────────────

    @Nested
    class NestedCompositionTests {

        @Test
        void childShorterThanParent_childWins() {
            var parent = DeadlineContext.NONE.withTimeout(Duration.ofSeconds(10));
            var child = parent.withTimeout(Duration.ofSeconds(2));
            assertThat(child.remainingTime().get().toMillis()).isLessThanOrEqualTo(2000);
        }

        @Test
        void childLongerThanParent_parentWins() {
            var parent = DeadlineContext.NONE.withTimeout(Duration.ofSeconds(2));
            var child = parent.withTimeout(Duration.ofSeconds(10));
            assertThat(child.remainingTime().get().toMillis()).isLessThanOrEqualTo(2000);
        }

        @Test
        void tripleNesting_tightestWins() {
            var outer = DeadlineContext.NONE.withTimeout(Duration.ofSeconds(30));
            var middle = outer.withTimeout(Duration.ofSeconds(5));
            var inner = middle.withTimeout(Duration.ofSeconds(60));
            assertThat(inner.remainingTime().get().toMillis()).isLessThanOrEqualTo(5000);
        }

        @Test
        void siblingDeadlines_independent() {
            var parent = DeadlineContext.NONE.withTimeout(Duration.ofSeconds(10));
            var childA = parent.withTimeout(Duration.ofSeconds(2));
            var childB = parent.withTimeout(Duration.ofSeconds(8));
            assertThat(childA.remainingTime().get().toMillis()).isLessThanOrEqualTo(2000);
            assertThat(childB.remainingTime().get().toMillis()).isLessThanOrEqualTo(8000);
            assertThat(childB.remainingTime().get().toMillis())
                    .isGreaterThan(childA.remainingTime().get().toMillis());
        }
    }
}
