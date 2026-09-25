package io.casehub.yaml.core.module;

import io.casehub.yaml.core.data.CsvParser;
import io.casehub.yaml.core.foreach.IterationGroup;
import io.casehub.yaml.core.resolver.VariableResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportExpanderTest {

    private final VariableResolver resolver = new VariableResolver(Map.of(), Set.of());

    @Test
    void inlineForEach_stampsImports() {
        var imp = new YamlImport("pipeline", "region", null,
                Map.of("ep", "${each.region}"),
                Map.of("as", "region", "in", List.of("us-east", "eu-west")),
                null);

        List<YamlImport> result = ImportExpander.expand(
                List.of(imp), Map.of(), Map.of(), resolver);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).as()).isEqualTo("region-us-east");
        assertThat(result.get(0).parameters()).containsEntry("ep", "us-east");
        assertThat(result.get(0).forEach()).isNull();
        assertThat(result.get(1).as()).isEqualTo("region-eu-west");
        assertThat(result.get(1).parameters()).containsEntry("ep", "eu-west");
    }

    @Test
    void namedGroup_stampsImports() {
        var groups = Map.of("regions",
                new IterationGroup("region", List.of("us", "eu")));
        var imp = new YamlImport("pipeline", "region", null,
                Map.of("r", "${each.region}"),
                "regions", null);

        List<YamlImport> result = ImportExpander.expand(
                List.of(imp), groups, Map.of(), resolver);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).as()).isEqualTo("region-us");
        assertThat(result.get(0).parameters()).containsEntry("r", "us");
        assertThat(result.get(1).as()).isEqualTo("region-eu");
    }

    @Test
    void csvDataSource_stampsWithResolvedParams() {
        var csv = CsvParser.parse("envs",
                "name:STRING,port:INTEGER\nstaging,8080\nprod,443");
        var groups = Map.of("envs", new IterationGroup("env", List.of()));
        var imp = new YamlImport("pipeline", "env", null,
                Map.of("host", "${each.env.name}", "p", "${each.env.port}"),
                "envs", null);

        List<YamlImport> result = ImportExpander.expand(
                List.of(imp), groups, Map.of("envs", csv), resolver);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).as()).isEqualTo("env-staging");
        assertThat(result.get(0).parameters()).containsEntry("host", "staging")
                                               .containsEntry("p", "8080");
        assertThat(result.get(1).as()).isEqualTo("env-prod");
        assertThat(result.get(1).parameters()).containsEntry("p", "443");
    }

    @Test
    void noForEach_passesThrough() {
        var imp = new YamlImport("mod", "alias", null,
                Map.of("k", "v"), null, null);

        List<YamlImport> result = ImportExpander.expand(
                List.of(imp), Map.of(), Map.of(), resolver);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(imp);
    }

    @Test
    void loop_preserved_on_stamped_imports() {
        var imp = new YamlImport("pipeline", "region", null,
                Map.of(), Map.of("as", "region", "in", List.of("us")),
                Map.of("count", 3));

        List<YamlImport> result = ImportExpander.expand(
                List.of(imp), Map.of(), Map.of(), resolver);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).loop()).isEqualTo(Map.of("count", 3));
        assertThat(result.get(0).forEach()).isNull();
    }

    @Test
    void mixed_forEach_and_regular_imports() {
        var regular = new YamlImport("db", "database", null,
                Map.of(), null, null);
        var forEach = new YamlImport("svc", "region", null,
                Map.of("r", "${each.region}"),
                Map.of("as", "region", "in", List.of("us", "eu")),
                null);

        List<YamlImport> result = ImportExpander.expand(
                List.of(regular, forEach), Map.of(), Map.of(), resolver);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).as()).isEqualTo("database");
        assertThat(result.get(1).as()).isEqualTo("region-us");
        assertThat(result.get(2).as()).isEqualTo("region-eu");
    }

    @Test
    void when_condition_filters_stamped_imports() {
        var imp = new YamlImport("pipeline", "region", "${each.region}",
                Map.of(),
                Map.of("as", "region", "in", List.of("true", "false")),
                null);

        List<YamlImport> result = ImportExpander.expand(
                List.of(imp), Map.of(), Map.of(), resolver);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).as()).isEqualTo("region-true");
    }

    @Test
    void dotInValue_throws() {
        var imp = new YamlImport("pipeline", "region", null,
                Map.of(),
                Map.of("as", "region", "in", List.of("us.east")),
                null);

        assertThatThrownBy(() -> ImportExpander.expand(
                List.of(imp), Map.of(), Map.of(), resolver))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".")
                .hasMessageContaining("reserved");
    }

    @Test
    void duplicateStampedAlias_throws() {
        var imp = new YamlImport("pipeline", "region", null,
                Map.of(),
                Map.of("as", "region", "in", List.of("same", "same")),
                null);

        assertThatThrownBy(() -> ImportExpander.expand(
                List.of(imp), Map.of(), Map.of(), resolver))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void stepImportsAreFilteredOut() {
        var moduleImport = new YamlImport("my-module", null, "mod", null, Map.of(), null, null);
        var stepImport   = new YamlImport(null, "trading-steps.yaml", null, null, null, null, null);

        List<YamlImport> result = ImportExpander.expand(
                List.of(moduleImport, stepImport), Map.of(), Map.of(), resolver);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).module()).isEqualTo("my-module");
    }
}
