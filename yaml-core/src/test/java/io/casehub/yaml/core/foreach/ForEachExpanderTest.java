package io.casehub.yaml.core.foreach;

import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.resolver.VariableSource;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForEachExpanderTest {

    record TestElement(String id, Map<String, Object> spec,
                       Object forEach, String when) {}

    static class TestAdapter implements ForEachAdapter<TestElement> {
        @Override
        public TestElement stamp(TestElement template, String stampedId,
                                 VariableResolver scopedResolver) {
            Map<String, Object> resolvedSpec = scopedResolver.resolveMap(
                    template.spec(), stampedId);
            return new TestElement(stampedId, resolvedSpec, null, null);
        }

        @Override
        public Object getForEach(TestElement element) { return element.forEach(); }

        @Override
        public String getId(TestElement element) { return element.id(); }

        @Override
        public String getWhen(TestElement element) { return element.when(); }
    }

    private final ForEachAdapter<TestElement> adapter = new TestAdapter();
    private final VariableResolver resolver = new VariableResolver(Map.of(), Set.of());

    @Test
    void inlineForEach_stampsThreeCopies() {
        Map<String, Object> inlineForEach = Map.of("as", "region",
                "in", List.of("us-east", "eu-west", "ap-south"));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("regional-source", new TestElement("regional-source",
                Map.of("name", "customers-${each.region}",
                       "uri", "s3://${each.region}/data.csv"),
                inlineForEach, null));

        var result = ForEachExpander.expand(elements, Map.of(), resolver,
                adapter, 1000);

        assertThat(result.elements()).hasSize(3);
        assertThat(result.elements().stream().map(TestElement::id).toList())
                .containsExactly("regional-source.us-east",
                        "regional-source.eu-west", "regional-source.ap-south");
        TestElement usEast = result.elements().stream()
                .filter(e -> e.id().equals("regional-source.us-east"))
                .findFirst().orElseThrow();
        assertThat(usEast.spec()).containsEntry("name", "customers-us-east")
                .containsEntry("uri", "s3://us-east/data.csv");
    }

    @Test
    void namedGroup_stampsFromSharedIterationGroup() {
        var groups = Map.of("regional",
                new IterationGroup("region", List.of("us-east", "eu-west")));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("regional-source", new TestElement("regional-source",
                Map.of("name", "${each.region}"),
                "regional", null));

        var result = ForEachExpander.expand(elements, groups, resolver,
                adapter, 1000);

        assertThat(result.elements()).hasSize(2);
        assertThat(result.elements().stream().map(TestElement::id).toList())
                .containsExactly("regional-source.us-east", "regional-source.eu-west");
    }

    @Test
    void twoForEach_sameGroup_expandIndependently() {
        var groups = Map.of("regional",
                new IterationGroup("region", List.of("us-east", "eu-west")));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("source", new TestElement("source",
                Map.of("name", "${each.region}"), "regional", null));
        elements.put("ingest", new TestElement("ingest",
                Map.of("name", "${each.region}-ingest"), "regional", null));

        var result = ForEachExpander.expand(elements, groups, resolver,
                adapter, 1000);

        assertThat(result.elements()).hasSize(4);
        assertThat(result.elements().stream().map(TestElement::id).toList())
                .containsExactly("source.us-east", "source.eu-west",
                        "ingest.us-east", "ingest.eu-west");
    }

    @Test
    void fixedElement_passesThrough() {
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("db", new TestElement("db",
                Map.of("name", "database"), null, null));

        var result = ForEachExpander.expand(elements, Map.of(), resolver,
                adapter, 1000);

        assertThat(result.elements()).hasSize(1);
        assertThat(result.elements().get(0).id()).isEqualTo("db");
        assertThat(result.elements().get(0).spec()).containsEntry("name", "database");
    }

    @Test
    void fixedElement_resolvesVariables() {
        var resolver = new VariableResolver(
                Map.of("var", (VariableSource) name -> "prod".equals(name) ? "production" : null),
                Set.of());
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("db", new TestElement("db",
                Map.of("env", "${var.prod}"), null, null));

        var result = ForEachExpander.expand(elements, Map.of(), resolver,
                adapter, 1000);

        assertThat(result.elements().get(0).spec()).containsEntry("env", "production");
    }

    @Test
    void mixedForEachAndFixed_correctCount() {
        var groups = Map.of("regional",
                new IterationGroup("region", List.of("us-east", "eu-west")));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("fixed-db", new TestElement("fixed-db",
                Map.of("name", "db"), null, null));
        elements.put("fixed-schema", new TestElement("fixed-schema",
                Map.of("name", "schema"), null, null));
        elements.put("regional-source", new TestElement("regional-source",
                Map.of("name", "${each.region}"), "regional", null));

        var result = ForEachExpander.expand(elements, groups, resolver,
                adapter, 1000);

        assertThat(result.elements()).hasSize(4);
    }

    @Test
    void expansionLimit_exceeded_throws() {
        Map<String, Object> inlineForEach = Map.of("as", "idx",
                "in", List.of("1", "2", "3", "4", "5"));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("node", new TestElement("node",
                Map.of("name", "${each.idx}"), inlineForEach, null));

        assertThatThrownBy(() -> ForEachExpander.expand(elements, Map.of(),
                resolver, adapter, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("node")
                .hasMessageContaining("5")
                .hasMessageContaining("3");
    }

    @Test
    void zeroValues_producesEmpty() {
        Map<String, Object> inlineForEach = Map.of("as", "idx", "in", List.of());
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("template", new TestElement("template",
                Map.of("name", "x"), inlineForEach, null));

        var result = ForEachExpander.expand(elements, Map.of(), resolver,
                adapter, 1000);

        assertThat(result.elements()).isEmpty();
    }

    // --- when conditions ---

    @Test
    void when_true_includes_element() {
        var resolver = new VariableResolver(
                Map.of("var", (VariableSource) name -> "enabled".equals(name) ? "true" : null),
                Set.of());
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("node", new TestElement("node",
                Map.of("name", "x"), null, "${var.enabled}"));

        var result = ForEachExpander.expand(elements, Map.of(), resolver,
                adapter, 1000);

        assertThat(result.elements()).hasSize(1);
        assertThat(result.excludedIds()).isEmpty();
    }

    @Test
    void when_false_excludes_element() {
        var resolver = new VariableResolver(
                Map.of("var", (VariableSource) name -> "enabled".equals(name) ? "false" : null),
                Set.of());
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("node", new TestElement("node",
                Map.of("name", "x"), null, "${var.enabled}"));

        var result = ForEachExpander.expand(elements, Map.of(), resolver,
                adapter, 1000);

        assertThat(result.elements()).isEmpty();
        assertThat(result.excludedIds()).containsExactly("node");
    }

    @Test
    void forEach_when_false_excludesAllCopies() {
        var resolver = new VariableResolver(
                Map.of("var", (VariableSource) name ->
                        "enable_sources".equals(name) ? "false" : null),
                Set.of());
        Map<String, Object> inlineForEach = Map.of("as", "region",
                "in", List.of("us-east", "eu-west"));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("source", new TestElement("source",
                Map.of("name", "${each.region}"),
                inlineForEach, "${var.enable_sources}"));

        var result = ForEachExpander.expand(elements, Map.of(), resolver,
                adapter, 1000);

        assertThat(result.elements()).isEmpty();
        assertThat(result.excludedIds())
                .containsExactlyInAnyOrder("source.us-east", "source.eu-west");
    }

    @Test
    void forEach_when_perCopy_selectiveExclusion() {
        Map<String, Object> inlineForEach = Map.of("as", "flag",
                "in", List.of("true", "false"));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("source", new TestElement("source",
                Map.of("name", "${each.flag}"),
                inlineForEach, "${each.flag}"));

        var result = ForEachExpander.expand(elements, Map.of(), resolver,
                adapter, 1000);

        assertThat(result.elements()).hasSize(1);
        assertThat(result.elements().get(0).id()).isEqualTo("source.true");
        assertThat(result.excludedIds()).containsExactly("source.false");
    }

    // --- variable resolution in iteration values ---

    @Test
    void inlineForEach_resolvesVariablesInValues() {
        var resolver = new VariableResolver(
                Map.of("var", (VariableSource) name ->
                        "suffix".equals(name) ? "prod" : null),
                Set.of());
        Map<String, Object> inlineForEach = Map.of("as", "env",
                "in", List.of("${var.suffix}"));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("node", new TestElement("node",
                Map.of("name", "${each.env}"), inlineForEach, null));

        var result = ForEachExpander.expand(elements, Map.of(), resolver,
                adapter, 1000);

        assertThat(result.elements()).hasSize(1);
        assertThat(result.elements().get(0).id()).isEqualTo("node.prod");
        assertThat(result.elements().get(0).spec()).containsEntry("name", "prod");
    }

    @Test
    void preserves_element_order() {
        var groups = Map.of("env",
                new IterationGroup("e", List.of("a", "b")));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("first", new TestElement("first",
                Map.of("name", "1"), null, null));
        elements.put("expand", new TestElement("expand",
                Map.of("name", "${each.e}"), "env", null));
        elements.put("last", new TestElement("last",
                Map.of("name", "3"), null, null));

        var result = ForEachExpander.expand(elements, groups, resolver,
                adapter, 1000);

        assertThat(result.elements().stream().map(TestElement::id).toList())
                .containsExactly("first", "expand.a", "expand.b", "last");
    }
}
