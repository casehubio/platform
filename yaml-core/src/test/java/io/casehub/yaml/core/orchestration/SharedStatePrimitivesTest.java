package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SharedStatePrimitivesTest {

    @Test
    void counter_incrementDecrement() {
        OrcCounter counter = new DefaultOrcCounter();
        counter.increment();
        counter.increment();
        counter.decrement();
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void counter_add() {
        OrcCounter counter = new DefaultOrcCounter();
        counter.add(100);
        counter.add(-30);
        assertThat(counter.get()).isEqualTo(70);
    }

    @Test
    void counter_reset() {
        OrcCounter counter = new DefaultOrcCounter();
        counter.add(50);
        counter.reset();
        assertThat(counter.get()).isEqualTo(0);
    }

    @Test
    void gauge_setAndGet() {
        OrcGauge<String> gauge = new DefaultOrcGauge<>();
        gauge.set("STRESSED");
        assertThat(gauge.get()).isEqualTo("STRESSED");
    }

    @Test
    void gauge_compareAndSet_success() {
        OrcGauge<String> gauge = new DefaultOrcGauge<>();
        gauge.set("NORMAL");
        boolean result = gauge.compareAndSet("NORMAL", "STRESSED");
        assertThat(result).isTrue();
        assertThat(gauge.get()).isEqualTo("STRESSED");
    }

    @Test
    void gauge_compareAndSet_failsOnMismatch() {
        OrcGauge<String> gauge = new DefaultOrcGauge<>();
        gauge.set("NORMAL");
        boolean result = gauge.compareAndSet("STRESSED", "CRITICAL");
        assertThat(result).isFalse();
        assertThat(gauge.get()).isEqualTo("NORMAL");
    }

    @Test
    void flag_setAndGet() {
        OrcFlag flag = new DefaultOrcFlag();
        assertThat(flag.get()).isFalse();
        flag.set();
        assertThat(flag.get()).isTrue();
    }

    @Test
    void flag_clear() {
        OrcFlag flag = new DefaultOrcFlag();
        flag.set();
        flag.clear();
        assertThat(flag.get()).isFalse();
    }

    @Test
    void flag_toggle() {
        OrcFlag flag = new DefaultOrcFlag();
        flag.toggle();
        assertThat(flag.get()).isTrue();
        flag.toggle();
        assertThat(flag.get()).isFalse();
    }

    @Test
    void accumulator_sum() {
        OrcAccumulator acc = new DefaultOrcAccumulator(Double::sum, 0.0);
        acc.accumulate(10.5);
        acc.accumulate(20.3);
        assertThat(acc.get()).isEqualTo(30.8);
    }

    @Test
    void accumulator_max() {
        OrcAccumulator acc = new DefaultOrcAccumulator(Double::max, Double.NEGATIVE_INFINITY);
        acc.accumulate(10.0);
        acc.accumulate(50.0);
        acc.accumulate(30.0);
        assertThat(acc.get()).isEqualTo(50.0);
    }

    @Test
    void accumulator_reset() {
        OrcAccumulator acc = new DefaultOrcAccumulator(Double::sum, 0.0);
        acc.accumulate(100.0);
        acc.reset();
        assertThat(acc.get()).isEqualTo(0.0);
    }

    @Test
    void map_putAndGet() {
        OrcMap<String, Integer> map = new DefaultOrcMap<>();
        map.put("AAPL", 100);
        assertThat(map.get("AAPL")).isEqualTo(100);
        assertThat(map.size()).isEqualTo(1);
    }

    @Test
    void map_putIfAbsent() {
        OrcMap<String, Integer> map = new DefaultOrcMap<>();
        map.put("AAPL", 100);
        Integer previous = map.putIfAbsent("AAPL", 200);
        assertThat(previous).isEqualTo(100);
        assertThat(map.get("AAPL")).isEqualTo(100);
    }

    @Test
    void map_computeIfAbsent() {
        OrcMap<String, Integer> map = new DefaultOrcMap<>();
        Integer result = map.computeIfAbsent("AAPL", k -> 42);
        assertThat(result).isEqualTo(42);
        Integer second = map.computeIfAbsent("AAPL", k -> 99);
        assertThat(second).isEqualTo(42);
    }

    @Test
    void map_merge() {
        OrcMap<String, Integer> map = new DefaultOrcMap<>();
        map.put("AAPL", 100);
        map.merge("AAPL", 50, Integer::sum);
        assertThat(map.get("AAPL")).isEqualTo(150);
    }

    @Test
    void map_remove() {
        OrcMap<String, Integer> map = new DefaultOrcMap<>();
        map.put("AAPL", 100);
        map.remove("AAPL");
        assertThat(map.containsKey("AAPL")).isFalse();
        assertThat(map.size()).isEqualTo(0);
    }
}
