package io.casehub.platform.coordination.protocol;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolDefinitionTest {

    @Test
    void constructsWithNameAndTemplates() {
        var templates = Map.of("vote", "I ${decision} this design");
        var def = new ProtocolDefinition("review", templates);
        assertThat(def.name()).isEqualTo("review");
        assertThat(def.templates()).containsExactlyEntriesOf(Map.of("vote", "I ${decision} this design"));
    }

    @Test
    void defensiveCopyPreventsExternalMutation() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision}");
        var def = new ProtocolDefinition("review", templates);
        templates.put("extra", "sneaky");
        assertThat(def.templates()).doesNotContainKey("extra");
    }

    @Test
    void templatesMapIsUnmodifiable() {
        var def = new ProtocolDefinition("review", Map.of("vote", "I ${decision}"));
        assertThatThrownBy(() -> def.templates().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullName() {
        assertThatThrownBy(() -> new ProtocolDefinition(null, Map.of("a", "b")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullTemplates() {
        assertThatThrownBy(() -> new ProtocolDefinition("review", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsEmptyTemplates() {
        assertThatThrownBy(() -> new ProtocolDefinition("review", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
