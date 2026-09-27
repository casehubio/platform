package io.casehub.yaml.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.yaml.core.step.MatchCase;
import io.casehub.yaml.core.step.MatchPattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MatchPatternDeserializerTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new YamlCoreJacksonModule());
    }

    @Test
    void deserializesValuePatternFromScalar() throws Exception {
        String yaml = """
                pattern: "ACTIVE"
                steps:
                  - action: activate
                """;
        MatchCase mc = mapper.readValue(yaml, MatchCase.class);
        assertThat(mc.pattern()).isInstanceOf(MatchPattern.ValuePattern.class);
        assertThat(((MatchPattern.ValuePattern) mc.pattern()).value()).isEqualTo("ACTIVE");
        assertThat(mc.steps()).hasSize(1);
    }

    @Test
    void deserializesValuePatternFromInteger() throws Exception {
        String yaml = """
                pattern: 42
                steps:
                  - action: handle
                """;
        MatchCase mc = mapper.readValue(yaml, MatchCase.class);
        assertThat(mc.pattern()).isInstanceOf(MatchPattern.ValuePattern.class);
        assertThat(((MatchPattern.ValuePattern) mc.pattern()).value()).isEqualTo(42);
    }

    @Test
    void deserializesStructuralPattern() throws Exception {
        String yaml = """
                pattern:
                  type: trade
                  priority: HIGH
                steps:
                  - action: escalate
                """;
        MatchCase mc = mapper.readValue(yaml, MatchCase.class);
        assertThat(mc.pattern()).isInstanceOf(MatchPattern.StructuralPattern.class);
        var sp = (MatchPattern.StructuralPattern) mc.pattern();
        assertThat(sp.fields()).containsEntry("type", "trade");
        assertThat(sp.fields()).containsEntry("priority", "HIGH");
    }

    @Test
    void deserializesGuard() throws Exception {
        String yaml = """
                pattern:
                  type: trade
                guard: "${match.amount} > 1000000"
                steps:
                  - action: escalate
                """;
        MatchCase mc = mapper.readValue(yaml, MatchCase.class);
        assertThat(mc.guard()).isEqualTo("${match.amount} > 1000000");
    }

    @Test
    void deserializesDefaultCase() throws Exception {
        String yaml = """
                default:
                  - action: log
                """;
        MatchCase mc = mapper.readValue(yaml, MatchCase.class);
        assertThat(mc.pattern()).isInstanceOf(MatchPattern.DefaultPattern.class);
        assertThat(mc.guard()).isNull();
        assertThat(mc.steps()).hasSize(1);
    }

    @Test
    void deserializesNullGuardWhenAbsent() throws Exception {
        String yaml = """
                pattern: "ACTIVE"
                steps:
                  - action: activate
                """;
        MatchCase mc = mapper.readValue(yaml, MatchCase.class);
        assertThat(mc.guard()).isNull();
    }

    @Test
    void deserializesMatchPatternDirectly() throws Exception {
        String yaml = "\"ACTIVE\"";
        MatchPattern mp = mapper.readValue(yaml, MatchPattern.class);
        assertThat(mp).isInstanceOf(MatchPattern.ValuePattern.class);
        assertThat(((MatchPattern.ValuePattern) mp).value()).isEqualTo("ACTIVE");
    }

    @Test
    void deserializesMatchPatternAsMap() throws Exception {
        String yaml = """
                type: trade
                """;
        MatchPattern mp = mapper.readValue(yaml, MatchPattern.class);
        assertThat(mp).isInstanceOf(MatchPattern.StructuralPattern.class);
        assertThat(((MatchPattern.StructuralPattern) mp).fields()).containsEntry("type", "trade");
    }
}
