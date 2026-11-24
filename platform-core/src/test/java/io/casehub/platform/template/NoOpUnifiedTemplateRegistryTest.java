package io.casehub.platform.template;

import io.casehub.template.registry.TemplateMatch;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpUnifiedTemplateRegistryTest {

    private final NoOpUnifiedTemplateRegistry registry = new NoOpUnifiedTemplateRegistry();

    @Test
    void matchReturnsUnmatched() {
        var result = registry.match("any input");
        assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
    }

    @Test
    void matchWithNamespaceReturnsUnmatched() {
        var result = registry.match("ns", "any input");
        assertThat(result).isInstanceOf(TemplateMatch.Unmatched.class);
    }

    @Test
    void generateThrows() {
        assertThatThrownBy(() -> registry.generate("ns", "t", Map.of()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void schemasReturnsEmpty() {
        assertThat(registry.schemas("ns")).isEmpty();
    }

    @Test
    void schemaReturnsEmpty() {
        assertThat(registry.schema("ns", "t")).isEmpty();
    }

    @Test
    void namespacesReturnsEmpty() {
        assertThat(registry.namespaces()).isEmpty();
    }
}
