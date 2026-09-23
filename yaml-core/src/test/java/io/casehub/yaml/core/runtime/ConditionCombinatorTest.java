package io.casehub.yaml.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionCombinatorTest {

    @Test
    void and_bothTrue_true() {
        Condition a = () -> true;
        Condition b = () -> true;
        assertThat(a.and(b).evaluate()).isTrue();
    }

    @Test
    void and_firstFalse_shortCircuits() {
        var count = new AtomicInteger(0);
        Condition a = () -> false;
        Condition b = () -> { count.incrementAndGet(); return true; };
        assertThat(a.and(b).evaluate()).isFalse();
        assertThat(count.get()).isZero();
    }

    @Test
    void and_secondFalse_false() {
        Condition a = () -> true;
        Condition b = () -> false;
        assertThat(a.and(b).evaluate()).isFalse();
    }

    @Test
    void or_firstTrue_shortCircuits() {
        var count = new AtomicInteger(0);
        Condition a = () -> true;
        Condition b = () -> { count.incrementAndGet(); return false; };
        assertThat(a.or(b).evaluate()).isTrue();
        assertThat(count.get()).isZero();
    }

    @Test
    void or_bothFalse_false() {
        Condition a = () -> false;
        Condition b = () -> false;
        assertThat(a.or(b).evaluate()).isFalse();
    }

    @Test
    void not_invertsTrueToFalse() {
        Condition a = () -> true;
        assertThat(a.not().evaluate()).isFalse();
    }

    @Test
    void not_invertsFalseToTrue() {
        Condition a = () -> false;
        assertThat(a.not().evaluate()).isTrue();
    }

    @Test
    void xor_sameBothTrue_false() {
        Condition a = () -> true;
        Condition b = () -> true;
        assertThat(a.xor(b).evaluate()).isFalse();
    }

    @Test
    void xor_sameBothFalse_false() {
        Condition a = () -> false;
        Condition b = () -> false;
        assertThat(a.xor(b).evaluate()).isFalse();
    }

    @Test
    void xor_different_true() {
        Condition a = () -> true;
        Condition b = () -> false;
        assertThat(a.xor(b).evaluate()).isTrue();
    }

    @Test
    void always_returnsTrue() {
        assertThat(Condition.always().evaluate()).isTrue();
    }

    @Test
    void never_returnsFalse() {
        assertThat(Condition.never().evaluate()).isFalse();
    }

    @Test
    void compositeChain_andOrNot() {
        Condition high = () -> true;
        Condition low = () -> false;
        assertThat(high.and(low.not()).or(low).evaluate()).isTrue();
    }

    @Test
    void always_and_x_isX() {
        Condition x = () -> false;
        assertThat(Condition.always().and(x).evaluate()).isFalse();
    }

    @Test
    void never_or_x_isX() {
        Condition x = () -> true;
        assertThat(Condition.never().or(x).evaluate()).isTrue();
    }
}
