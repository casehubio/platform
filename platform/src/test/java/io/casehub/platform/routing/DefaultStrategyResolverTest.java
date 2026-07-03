package io.casehub.platform.routing;

import io.casehub.platform.api.routing.NamedStrategy;
import io.casehub.platform.api.routing.StrategyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DefaultStrategyResolverTest {

    interface TestStrategy extends NamedStrategy {
        String value();
    }

    static class StrategyA implements TestStrategy {
        @Override public String id() { return "a"; }
        @Override public String value() { return "alpha"; }
    }

    static class StrategyB implements TestStrategy {
        @Override public String id() { return "b"; }
        @Override public String value() { return "beta"; }
    }

    interface OtherStrategy extends NamedStrategy {}

    private StrategyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultStrategyResolver(
            List.of(new StrategyA(), new StrategyB()));
    }

    @Test
    void resolveByIdReturnsCorrectStrategy() {
        TestStrategy result = resolver.resolve(TestStrategy.class, "a");
        assertThat(result.value()).isEqualTo("alpha");
    }

    @Test
    void resolveByIdReturnsSecondStrategy() {
        TestStrategy result = resolver.resolve(TestStrategy.class, "b");
        assertThat(result.value()).isEqualTo("beta");
    }

    @Test
    void resolveUnknownIdThrows() {
        assertThatThrownBy(() -> resolver.resolve(TestStrategy.class, "unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown");
    }

    @Test
    void resolveNullIdReturnsDefault() {
        TestStrategy result = resolver.resolve(TestStrategy.class, null);
        assertThat(result).isNotNull();
    }

    @Test
    void findByIdReturnsOptional() {
        assertThat(resolver.find(TestStrategy.class, "a")).isPresent();
        assertThat(resolver.find(TestStrategy.class, "unknown")).isEmpty();
    }

    @Test
    void availableListsAllForType() {
        List<TestStrategy> strategies = resolver.available(TestStrategy.class);
        assertThat(strategies).hasSize(2);
        assertThat(strategies).extracting(NamedStrategy::id).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void availableForUnregisteredTypeReturnsEmpty() {
        List<OtherStrategy> strategies = resolver.available(OtherStrategy.class);
        assertThat(strategies).isEmpty();
    }

    @Test
    void duplicateIdsThrowAtConstruction() {
        assertThatThrownBy(() -> new DefaultStrategyResolver(
            List.of(new StrategyA(), new StrategyA())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate");
    }
}
