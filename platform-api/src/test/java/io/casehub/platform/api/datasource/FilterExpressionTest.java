package io.casehub.platform.api.datasource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilterExpressionTest {

    @Test
    void test_delegatesToPredicate() {
        FilterExpression<Integer> expr = new FilterExpression<>("jq", ". > 5", i -> i > 5);
        assertThat(expr.test(10)).isTrue();
        assertThat(expr.test(3)).isFalse();
    }

    @Test
    void accessors() {
        FilterExpression<String> expr = new FilterExpression<>("mvel", "name != null", s -> s != null);
        assertThat(expr.type()).isEqualTo("mvel");
        assertThat(expr.expression()).isEqualTo("name != null");
    }

    @Test
    void nullsRejected() {
        assertThatThrownBy(() -> new FilterExpression<>(null, "expr", s -> true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FilterExpression<>("jq", null, s -> true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FilterExpression<>("jq", "expr", null))
                .isInstanceOf(NullPointerException.class);
    }
}
