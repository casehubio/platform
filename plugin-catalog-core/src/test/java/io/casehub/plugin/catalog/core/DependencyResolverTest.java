package io.casehub.plugin.catalog.core;

import io.casehub.plugin.catalog.PluginCoordinates;
import io.casehub.plugin.catalog.PluginManifest;
import io.casehub.plugin.catalog.PluginPackage;
import io.casehub.plugin.catalog.PluginRequirements;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencyResolverTest {

    private final DependencyResolver resolver = new DependencyResolver();

    @Test
    void resolve_emptyCollection() {
        assertThat(resolver.resolve(List.of())).isEmpty();
    }

    @Test
    void resolve_noDependencies_preservesOrder() {
        var a = pkg("a", Set.of("cap-a"), List.of());
        var b = pkg("b", Set.of("cap-b"), List.of());
        assertThat(resolver.resolve(List.of(a, b)))
                .extracting(p -> p.coordinates().name())
                .containsExactly("a", "b");
    }

    @Test
    void resolve_ordersProviderBeforeConsumer() {
        var provider = pkg("provider", Set.of("rest-request"), List.of());
        var consumer = pkg("consumer", Set.of("consumer-cap"),
                List.of("rest-request"));

        var result = resolver.resolve(List.of(consumer, provider));

        assertThat(result)
                .extracting(p -> p.coordinates().name())
                .containsExactly("provider", "consumer");
    }

    @Test
    void resolve_detectsCycle() {
        var a = pkg("a", Set.of("cap-a"), List.of("cap-b"));
        var b = pkg("b", Set.of("cap-b"), List.of("cap-a"));

        assertThatThrownBy(() -> resolver.resolve(List.of(a, b)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void resolve_transitiveChain() {
        var c = pkg("c", Set.of("cap-c"), List.of("cap-b"));
        var b = pkg("b", Set.of("cap-b"), List.of("cap-a"));
        var a = pkg("a", Set.of("cap-a"), List.of());

        var result = resolver.resolve(List.of(c, b, a));

        assertThat(result)
                .extracting(p -> p.coordinates().name())
                .containsExactly("a", "b", "c");
    }

    private static PluginPackage pkg(String name, Set<String> provides,
                                      List<String> requiresPrimitives) {
        return new PluginPackage(
                new TestManifest(name, provides, requiresPrimitives),
                Path.of("/plugins/" + name), List.of(), List.of());
    }

    private record TestManifest(String name, Set<String> provides,
                                List<String> requiresPrimitives) implements PluginManifest {
        @Override public PluginCoordinates coordinates() {
            return new PluginCoordinates(name, "1.0.0");
        }
        @Override public String description() { return name; }
        @Override public PluginRequirements requirements() {
            return new PluginRequirements(
                    Optional.empty(), requiresPrimitives, List.of());
        }
    }
}
