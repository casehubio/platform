package io.casehub.platform.label;

import io.casehub.platform.api.label.LabelDescriptor;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpLabelRegistryTest {

    private final NoOpLabelRegistry registry = new NoOpLabelRegistry();

    @Test
    void resolveReturnsEmpty() {
        assertThat(registry.resolve("t1", Path.of("IT"))).isEmpty();
    }

    @Test
    void childrenReturnsEmpty() {
        assertThat(registry.children("t1", Path.root())).isEmpty();
    }

    @Test
    void subtreeReturnsEmpty() {
        assertThat(registry.subtree("t1", Path.root())).isEmpty();
    }

    @Test
    void registerDoesNotThrow() {
        registry.register("t1", new LabelDescriptor("t1", Path.of("IT"), "IT", 0));
    }

    @Test
    void deregisterDoesNotThrow() {
        registry.deregister("t1", Path.of("IT"));
    }
}
