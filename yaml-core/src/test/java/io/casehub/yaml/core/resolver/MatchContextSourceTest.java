package io.casehub.yaml.core.resolver;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MatchContextSourceTest {

    @Test
    void resolvesScalarMatchValue() {
        var source = VariableSource.matchContext("ACTIVE");
        assertThat(source.resolve("match")).isEqualTo("ACTIVE");
    }

    @Test
    void resolvesFieldAccessOnMap() {
        var source = VariableSource.matchContext(
                Map.of("type", "trade", "amount", 1500));
        assertThat(source.resolve("match.type")).isEqualTo("trade");
        assertThat(source.resolve("match.amount")).isEqualTo("1500");
    }

    @Test
    void returnsNullForMissingField() {
        var source = VariableSource.matchContext(
                Map.of("type", "trade"));
        assertThat(source.resolve("match.missing")).isNull();
    }

    @Test
    void returnsNullForNonMatchPrefix() {
        var source = VariableSource.matchContext("ACTIVE");
        assertThat(source.resolve("other")).isNull();
        assertThat(source.resolve("each.value")).isNull();
    }

    @Test
    void fieldAccessOnScalarReturnsNull() {
        var source = VariableSource.matchContext("ACTIVE");
        assertThat(source.resolve("match.field")).isNull();
    }

    @Test
    void resolvesNestedFieldAccess() {
        var source = VariableSource.matchContext(
                Map.of("meta", Map.of("version", 2)));
        assertThat(source.resolve("match.meta.version")).isEqualTo("2");
    }

    @Test
    void nullScrutineeReturnsNullString() {
        var source = VariableSource.matchContext(null);
        assertThat(source.resolve("match")).isEqualTo("null");
    }

    @Test
    void entireMatchValueToStringForMap() {
        var scrutinee = Map.of("type", "trade");
        var source = VariableSource.matchContext(scrutinee);
        assertThat(source.resolve("match")).isEqualTo(scrutinee.toString());
    }
}
