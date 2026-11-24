package io.casehub.template.registry;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateNamespaceTest {

    @Test
    void validConstruction() {
        var def = new TemplateDefinition("vote", List.of("pattern"), Map.of());
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("vote", def);
        var ns = new TemplateNamespace("protocol:review", templates);

        assertThat(ns.name()).isEqualTo("protocol:review");
        assertThat(ns.templates()).containsKey("vote");
    }

    @Test
    void nullNameThrows() {
        var def = new TemplateDefinition("vote", List.of("p"), Map.of());
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("vote", def);
        assertThatThrownBy(() -> new TemplateNamespace(null, templates))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptyTemplatesThrows() {
        assertThatThrownBy(() -> new TemplateNamespace("ns", Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keyNameMismatchThrows() {
        var def = new TemplateDefinition("vote", List.of("p"), Map.of());
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("wrong-key", def);
        assertThatThrownBy(() -> new TemplateNamespace("ns", templates))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("wrong-key")
            .hasMessageContaining("vote");
    }

    @Test
    void defensiveCopy() {
        var def = new TemplateDefinition("vote", List.of("p"), Map.of());
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("vote", def);
        var ns = new TemplateNamespace("ns", templates);
        templates.put("extra", new TemplateDefinition("extra", List.of("p"), Map.of()));
        assertThat(ns.templates()).hasSize(1);
    }
}
