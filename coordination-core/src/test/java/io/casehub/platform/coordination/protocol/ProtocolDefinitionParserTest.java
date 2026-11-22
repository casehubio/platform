package io.casehub.platform.coordination.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolDefinitionParserTest {

    @Test
    void parsesNameAndTemplatesFromYaml() {
        String yaml = """
                name: review
                templates:
                  approve: "I ${decision} this design"
                  escalate: "I escalate to ${target}"
                """;
        ProtocolDefinition def = ProtocolDefinitionParser.parse("review", toStream(yaml));
        assertThat(def.name()).isEqualTo("review");
        assertThat(def.templates()).hasSize(2);
        assertThat(def.templates()).containsEntry("approve", "I ${decision} this design");
        assertThat(def.templates()).containsEntry("escalate", "I escalate to ${target}");
    }

    @Test
    void parsesFromString() {
        String yaml = """
                name: review
                templates:
                  vote: "I ${decision} this"
                """;
        ProtocolDefinition def = ProtocolDefinitionParser.parse("review", yaml);
        assertThat(def.name()).isEqualTo("review");
        assertThat(def.templates()).containsEntry("vote", "I ${decision} this");
    }

    @Test
    void parsesOptionalSegments() {
        String yaml = """
                name: review
                templates:
                  approve: "I ${decision} this design[ because ${rationale}]"
                """;
        ProtocolDefinition def = ProtocolDefinitionParser.parse("review", toStream(yaml));
        assertThat(def.templates()).containsEntry("approve", "I ${decision} this design[ because ${rationale}]");
    }

    @Test
    void rejectsInvalidVariableName() {
        String yaml = """
                name: review
                templates:
                  bad: "I ${1invalid} this"
                """;
        assertThatThrownBy(() -> ProtocolDefinitionParser.parse("review", toStream(yaml)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad");
    }

    @Test
    void rejectsMissingTemplatesSection() {
        String yaml = """
                name: review
                """;
        assertThatThrownBy(() -> ProtocolDefinitionParser.parse("review", toStream(yaml)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("templates");
    }

    @Test
    void rejectsEmptyTemplates() {
        String yaml = """
                name: review
                templates:
                """;
        assertThatThrownBy(() -> ProtocolDefinitionParser.parse("review", toStream(yaml)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("templates");
    }

    @Test
    void rejectsNullName() {
        String yaml = """
                name: review
                templates:
                  vote: "I ${decision}"
                """;
        assertThatThrownBy(() -> ProtocolDefinitionParser.parse(null, toStream(yaml)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> ProtocolDefinitionParser.parse("review", (InputStream) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullStringInput() {
        assertThatThrownBy(() -> ProtocolDefinitionParser.parse("review", (String) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nameParameterOverridesYamlName() {
        String yaml = """
                name: yaml-name
                templates:
                  vote: "I ${decision}"
                """;
        ProtocolDefinition def = ProtocolDefinitionParser.parse("override-name", toStream(yaml));
        assertThat(def.name()).isEqualTo("override-name");
    }

    @Test
    void parsesWithoutNameFieldInYaml() {
        String yaml = """
                templates:
                  vote: "I ${decision}"
                """;
        ProtocolDefinition def = ProtocolDefinitionParser.parse("provided-name", toStream(yaml));
        assertThat(def.name()).isEqualTo("provided-name");
    }

    @Test
    void rejectsNonMapTemplatesValue() {
        String yaml = """
                name: review
                templates: "not a map"
                """;
        assertThatThrownBy(() -> ProtocolDefinitionParser.parse("review", toStream(yaml)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("templates");
    }

    @Test
    void preservesTemplateOrder() {
        String yaml = """
                name: review
                templates:
                  charlie: "text ${c}"
                  alpha: "text ${a}"
                  bravo: "text ${b}"
                """;
        ProtocolDefinition def = ProtocolDefinitionParser.parse("review", toStream(yaml));
        assertThat(def.templates().keySet()).containsExactly("charlie", "alpha", "bravo");
    }

    @Test
    void preservesBooleanLikeWordsAsStrings() {
        String yaml = """
                      name: review
                      templates:
                        confirm: "yes"
                        deny: "no"
                      """;
        ProtocolDefinition def = ProtocolDefinitionParser.parse("review", toStream(yaml));
        assertThat(def.templates()).containsEntry("confirm", "yes");
        assertThat(def.templates()).containsEntry("deny", "no");
    }


    private static InputStream toStream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
