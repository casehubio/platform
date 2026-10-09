package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.orchestration.DefaultExecutionScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrimitiveVariableSourceTest {

    @Test
    void resolvesCounterValue() {
        var scope = new DefaultExecutionScope();
        scope.counter("supply").add(42);
        var source = new PrimitiveVariableSource(scope);
        assertThat(source.resolve("counter.supply")).isEqualTo("42");
    }

    @Test
    void resolvesFlagValue() {
        var scope = new DefaultExecutionScope();
        scope.flag("ready").set();
        var source = new PrimitiveVariableSource(scope);
        assertThat(source.resolve("flag.ready")).isEqualTo("true");
    }

    @Test
    void resolvesFlagFalse() {
        var scope = new DefaultExecutionScope();
        scope.flag("ready");
        var source = new PrimitiveVariableSource(scope);
        assertThat(source.resolve("flag.ready")).isEqualTo("false");
    }

    @Test
    void resolvesSignalState() {
        var scope = new DefaultExecutionScope();
        scope.signal("done").signal();
        var source = new PrimitiveVariableSource(scope);
        assertThat(source.resolve("signal.done")).isEqualTo("true");
    }

    @Test
    void resolvesSignalNotFired() {
        var scope = new DefaultExecutionScope();
        scope.signal("done");
        var source = new PrimitiveVariableSource(scope);
        assertThat(source.resolve("signal.done")).isEqualTo("false");
    }

    @Test
    void resolvesAccumulatorValue() {
        var scope = new DefaultExecutionScope();
        scope.accumulator("total", Double::sum, 0.0).accumulate(3.14);
        var source = new PrimitiveVariableSource(scope);
        assertThat(source.resolve("accumulator.total")).isEqualTo("3.14");
    }

    @Test
    void resolvesGaugeValue() {
        var scope = new DefaultExecutionScope();
        scope.<String>gauge("status").set("active");
        var source = new PrimitiveVariableSource(scope);
        assertThat(source.resolve("gauge.status")).isEqualTo("active");
    }

    @Test
    void returnsNullForUnknownPrimitive() {
        var scope = new DefaultExecutionScope();
        var source = new PrimitiveVariableSource(scope);
        assertThat(source.resolve("counter.nonexistent")).isNull();
    }

    @Test
    void returnsNullForUnknownPrefix() {
        var scope = new DefaultExecutionScope();
        var source = new PrimitiveVariableSource(scope);
        assertThat(source.resolve("unknown.something")).isNull();
    }

    @Test
    void returnsNullForNoDot() {
        var scope = new DefaultExecutionScope();
        var source = new PrimitiveVariableSource(scope);
        assertThat(source.resolve("nodot")).isNull();
    }
}
