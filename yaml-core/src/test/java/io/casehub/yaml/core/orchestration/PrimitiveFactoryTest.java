package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PrimitiveFactoryTest {

    @Test
    void counter_sameNameSameInstance() {
        var scope = new DefaultScenarioScope();
        var c1 = scope.counter("events");
        var c2 = scope.counter("events");
        assertThat(c1).isSameAs(c2);
        scope.close();
    }

    @Test
    void gauge_sameNameSameInstance() {
        var scope = new DefaultScenarioScope();
        OrcGauge<String> g1 = scope.gauge("state");
        OrcGauge<String> g2 = scope.gauge("state");
        assertThat(g1).isSameAs(g2);
        scope.close();
    }

    @Test
    void flag_sameNameSameInstance() {
        var scope = new DefaultScenarioScope();
        var f1 = scope.flag("ready");
        var f2 = scope.flag("ready");
        assertThat(f1).isSameAs(f2);
        scope.close();
    }

    @Test
    void accumulator_sameNameSameInstance() {
        var scope = new DefaultScenarioScope();
        var a1 = scope.accumulator("total", Double::sum, 0.0);
        var a2 = scope.accumulator("total", Double::sum, 0.0);
        assertThat(a1).isSameAs(a2);
        scope.close();
    }

    @Test
    void map_sameNameSameInstance() {
        var scope = new DefaultScenarioScope();
        OrcMap<String, Integer> m1 = scope.map("positions");
        OrcMap<String, Integer> m2 = scope.map("positions");
        assertThat(m1).isSameAs(m2);
        scope.close();
    }

    @Test
    void customFactory_substitutesChannelCreation() throws InterruptedException {
        var factoryCalls = new AtomicInteger(0);
        var delegate     = new DefaultPrimitiveFactory();
        PrimitiveFactory custom = new PrimitiveFactory() {
            @Override
            public OrcSemaphore createSemaphore(String name, int permits)                                                             {return delegate.createSemaphore(name, permits);}

            @Override
            public OrcSemaphore createSemaphore(String name, int permits, java.time.Duration window)                                  {return delegate.createSemaphore(name, permits, window);}

            @Override
            public OrcLatch createLatch(String name, int count)                                                                       {return delegate.createLatch(name, count);}

            @Override
            public OrcSignal createSignal(String name)                                                                                {return delegate.createSignal(name);}

            @Override
            public <T> OrcChannel<T> createChannel(String name)                                                                       {
                                                                                                                                          factoryCalls.incrementAndGet();
                                                                                                                                          return delegate.createChannel(name);
                                                                                                                                      }

            @Override
            public <T> OrcChannel<T> createChannel(String name, int capacity)                                                         {return delegate.createChannel(name, capacity);}

            @Override
            public <S extends Enum<S>> BlockingOrcStateMachine<S> createStateMachine(String name, Class<S> stateType, S initialState) {return delegate.createStateMachine(name, stateType, initialState);}

            @Override
            public OrcCounter createCounter(String name)                                                                              {return delegate.createCounter(name);}

            @Override
            public <T> OrcGauge<T> createGauge(String name)                                                                           {return delegate.createGauge(name);}

            @Override
            public OrcFlag createFlag(String name)                                                                                    {return delegate.createFlag(name);}

            @Override
            public OrcAccumulator createAccumulator(String name, java.util.function.DoubleBinaryOperator op, double identity)         {return delegate.createAccumulator(name, op, identity);}

            @Override
            public <K, V> OrcMap<K, V> createMap(String name)                                                                         {return delegate.createMap(name);}
        };

        var                scope = new DefaultScenarioScope(custom);
        OrcChannel<String> ch1   = scope.channel("test");
        OrcChannel<String> ch2   = scope.channel("test");

        assertThat(ch1).isSameAs(ch2);
        assertThat(factoryCalls.get()).isEqualTo(1);
        scope.close();
    }

    @Test
    void counter_usable_afterCreation() {
        var scope = new DefaultScenarioScope();
        var counter = scope.counter("events");
        counter.increment();
        counter.increment();
        assertThat(counter.get()).isEqualTo(2);
        scope.close();
    }

    @Test
    void map_usable_afterCreation() {
        var scope = new DefaultScenarioScope();
        OrcMap<String, Integer> map = scope.map("positions");
        map.put("AAPL", 100);
        assertThat(map.get("AAPL")).isEqualTo(100);
        scope.close();
    }

    @Test
    void primitive_lookupFindsNewTypes() {
        var scope = new DefaultScenarioScope();
        scope.counter("events");
        var found = scope.primitive("events", OrcCounter.class);
        assertThat(found).isNotNull();
        scope.close();
    }
}
