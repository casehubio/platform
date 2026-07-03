package io.casehub.platform.api.routing;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NamedStrategyContractTest {

    interface TestStrategy extends NamedStrategy {
        String doWork();
    }

    static class ConcreteStrategy implements TestStrategy {
        private final String id;
        ConcreteStrategy(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public String doWork() { return "done"; }
    }

    @Test
    void idIsStableKey() {
        var strategy = new ConcreteStrategy("my-strategy");
        assertThat(strategy.id()).isEqualTo("my-strategy");
    }

    @Test
    void extendingInterfacePreservesMarker() {
        var strategy = new ConcreteStrategy("test");
        assertThat(strategy).isInstanceOf(NamedStrategy.class);
    }
}
