package io.casehub.yaml.core.module;

import io.casehub.yaml.core.foreach.ForEachDirective;
import io.casehub.yaml.core.orchestration.LoopDirective;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YamlImportTest {

    @Test
    void construct_with_forEach_and_loop() {
        var imp = new YamlImport("my-module", "region", null, Map.of(),
                "regions", Map.of("count", 3));
        assertThat(imp.forEach()).isEqualTo("regions");
        assertThat(imp.loop()).isNotNull();
    }

    @Test
    void construct_without_forEach_and_loop() {
        var imp = new YamlImport("my-module", "region", null, Map.of(), null, null);
        assertThat(imp.forEach()).isNull();
        assertThat(imp.loop()).isNull();
    }

    @Test
    void forEach_parses_via_ForEachDirective() {
        var imp = new YamlImport("mod", "region", null, Map.of(),
                Map.of("as", "r", "in", List.of("us", "eu")), null);
        ForEachDirective directive = ForEachDirective.parse(imp.forEach());
        assertThat(directive).isInstanceOf(ForEachDirective.InlineIteration.class);
    }

    @Test
    void loop_parses_via_LoopDirective() {
        var imp = new YamlImport("mod", "region", null, Map.of(),
                null, 5);
        LoopDirective directive = LoopDirective.parse(imp.loop());
        assertThat(directive).isInstanceOf(LoopDirective.Count.class);
        assertThat(((LoopDirective.Count) directive).count()).isEqualTo(5);
    }

    @Test
    void parameters_default_to_empty_map() {
        var imp = new YamlImport("mod", "x", null, null, null, null);
        assertThat(imp.parameters()).isEmpty();
    }
}
