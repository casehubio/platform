package io.casehub.yaml.core.foreach;

import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.resolver.VariableSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForEachExpanderTest {

    record TestElement(String id, Map<String, Object> spec,
                       ForEachDirective forEach, String when) {}

    static class TestAdapter implements ForEachAdapter<TestElement> {
        @Override
        public TestElement stamp(TestElement template, String stampedId,
                                 VariableResolver scopedResolver) {
            Map<String, Object> resolvedSpec = scopedResolver.resolveMap(
                    template.spec(), stampedId);
            return new TestElement(stampedId, resolvedSpec, null, null);
        }

        @Override
        public ForEachDirective getForEach(TestElement element) { return element.forEach(); }

        @Override
        public String getId(TestElement element) { return element.id(); }

        @Override
        public String getWhen(TestElement element) { return element.when(); }
    }

    private final ForEachAdapter<TestElement> adapter = new TestAdapter();
    private final VariableResolver resolver = new VariableResolver(Map.of(), Set.of());

    @Test
    void inlineForEach_stampsThreeCopies() {
        var inlineForEach = new ForEachDirective.InlineIteration("region",
                List.of("us-east", "eu-west", "ap-south"));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("regional-source", new TestElement("regional-source",
                Map.of("name", "customers-${each.region}",
                       "uri", "s3://${each.region}/data.csv"),
                inlineForEach, null));

        var result = ForEachExpander.expand(elements, Map.of(), resolver,
                adapter, 1000);

        assertThat(result.elements()).hasSize(3);
        assertThat(new ArrayList<>(result.elements().keySet()))
                .containsExactly("regional-source.us-east",
                        "regional-source.eu-west", "regional-source.ap-south");
        TestElement usEast = result.elements().get("regional-source.us-east");
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
                new ForEachDirective.GroupRef("regional"), null));

        var result = ForEachExpander.expand(elements, groups, resolver,
                adapter, 1000);

        assertThat(result.elements()).hasSize(2);
        assertThat(new ArrayList<>(result.elements().keySet()))
                .containsExactly("regional-source.us-east", "regional-source.eu-west");
    }

    @Test
    void twoForEach_sameGroup_expandIndependently() {
        var groups = Map.of("regional",
                new IterationGroup("region", List.of("us-east", "eu-west")));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("source", new TestElement("source",
                Map.of("name", "${each.region}"), new ForEachDirective.GroupRef("regional"), null));
        elements.put("ingest", new TestElement("ingest",
                Map.of("name", "${each.region}-ingest"), new ForEachDirective.GroupRef("regional"), null));

        var result = ForEachExpander.expand(elements, groups, resolver,
                adapter, 1000);

        assertThat(result.elements()).hasSize(4);
        assertThat(new ArrayList<>(result.elements().keySet()))
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
        assertThat(result.elements().get("db").id()).isEqualTo("db");
        assertThat(result.elements().get("db").spec()).containsEntry("name", "database");
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

        assertThat(result.elements().get("db").spec()).containsEntry("env", "production");
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
                Map.of("name", "${each.region}"), new ForEachDirective.GroupRef("regional"), null));

        var result = ForEachExpander.expand(elements, groups, resolver,
                adapter, 1000);

        assertThat(result.elements()).hasSize(4);
    }

    @Test
    void expansionLimit_exceeded_throws() {
        var inlineForEach = new ForEachDirective.InlineIteration("idx",
                List.of("1", "2", "3", "4", "5"));
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
        var inlineForEach = new ForEachDirective.InlineIteration("idx", List.of());
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
        var inlineForEach = new ForEachDirective.InlineIteration("region",
                List.of("us-east", "eu-west"));
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
        var inlineForEach = new ForEachDirective.InlineIteration("flag",
                List.of("true", "false"));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("source", new TestElement("source",
                Map.of("name", "${each.flag}"),
                inlineForEach, "${each.flag}"));

        var result = ForEachExpander.expand(elements, Map.of(), resolver,
                adapter, 1000);

        assertThat(result.elements()).hasSize(1);
        assertThat(result.elements().containsKey("source.true")).isTrue();
        assertThat(result.excludedIds()).containsExactly("source.false");
    }

    // --- variable resolution in iteration values ---

    @Test
    void inlineForEach_resolvesVariablesInValues() {
        var resolver = new VariableResolver(
                Map.of("var", (VariableSource) name ->
                        "suffix".equals(name) ? "prod" : null),
                Set.of());
        var inlineForEach = new ForEachDirective.InlineIteration("env",
                List.of("${var.suffix}"));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("node", new TestElement("node",
                Map.of("name", "${each.env}"), inlineForEach, null));

        var result = ForEachExpander.expand(elements, Map.of(), resolver,
                adapter, 1000);

        assertThat(result.elements()).hasSize(1);
        assertThat(result.elements().get("node.prod").id()).isEqualTo("node.prod");
        assertThat(result.elements().get("node.prod").spec()).containsEntry("name", "prod");
    }

    @Test
    void preserves_element_order() {
        var groups = Map.of("env",
                new IterationGroup("e", List.of("a", "b")));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("first", new TestElement("first",
                Map.of("name", "1"), null, null));
        elements.put("expand", new TestElement("expand",
                Map.of("name", "${each.e}"), new ForEachDirective.GroupRef("env"), null));
        elements.put("last", new TestElement("last",
                Map.of("name", "3"), null, null));

        var result = ForEachExpander.expand(elements, groups, resolver,
                adapter, 1000);

        assertThat(new ArrayList<>(result.elements().keySet()))
                .containsExactly("first", "expand.a", "expand.b", "last");
    }

    @Test
    void duplicate_forEach_values_throws() {
        var inlineForEach = new ForEachDirective.InlineIteration("x",
                                                   List.of("same", "same"));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("node", new TestElement("node",
                                             Map.of("name", "${each.x}"), inlineForEach, null));

        assertThatThrownBy(() -> ForEachExpander.expand(elements, Map.of(),
                                                        resolver, adapter, 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate stamped ID")
                .hasMessageContaining("node.same");
    }


// --- IterationValueExpander ---

    @Test
    void valueExpander_splits_values() {
        var groups = Map.of("regional",
                            new IterationGroup("region", List.of("us-east|eu-west")));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("source", new TestElement("source",
                                               Map.of("name", "${each.region}"), new ForEachDirective.GroupRef("regional"), null));

        IterationValueExpander expander = (resolved, ctx) -> {
            if (resolved.contains("|")) {
                return List.of(resolved.split("\\|"));
            }
            return List.of(resolved);
        };

        var result = ForEachExpander.expand(elements, groups, resolver,
                                            adapter, 1000, expander);

        assertThat(result.elements()).hasSize(2);
        assertThat(result.elements().containsKey("source.us-east")).isTrue();
        assertThat(result.elements().containsKey("source.eu-west")).isTrue();
    }

    @Test
    void valueExpander_null_uses_default() {
        var inlineForEach = new ForEachDirective.InlineIteration("x",
                                                   List.of("a", "b"));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("node", new TestElement("node",
                                             Map.of("name", "${each.x}"), inlineForEach, null));

        var result = ForEachExpander.expand(elements, Map.of(), resolver,
                                            adapter, 1000, null);

        assertThat(result.elements()).hasSize(2);
    }

    @Test
    void valueExpander_single_passthrough() {
        var groups = Map.of("env",
                            new IterationGroup("e", List.of("prod")));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("node", new TestElement("node",
                                             Map.of("name", "${each.e}"), new ForEachDirective.GroupRef("env"), null));

        IterationValueExpander expander = (resolved, ctx) -> List.of(resolved);

        var result = ForEachExpander.expand(elements, groups, resolver,
                                            adapter, 1000, expander);

        assertThat(result.elements()).hasSize(1);
        assertThat(result.elements().containsKey("node.prod")).isTrue();
    }

    @Test
    void valueExpander_error_wraps_context() {
        var groups = Map.of("bad",
                            new IterationGroup("x", List.of("boom")));
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("node", new TestElement("node",
                                             Map.of("name", "${each.x}"), new ForEachDirective.GroupRef("bad"), null));

        IterationValueExpander expander = (resolved, ctx) -> {
            throw new RuntimeException("parse failed");
        };

        assertThatThrownBy(() -> ForEachExpander.expand(elements, groups,
                                                        resolver, adapter, 1000, expander))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad")
                .hasCauseInstanceOf(RuntimeException.class);
    }

// --- Reference rewriting ---

    record RefElement(String id, Map<String, Object> spec,
                      ForEachDirective forEach, String when,
                      List<ForEachAdapter.Reference> refs) {}

    static class RefAdapter implements ForEachAdapter<RefElement> {
        @Override
        public RefElement stamp(RefElement template, String stampedId,
                                VariableResolver scopedResolver) {
            return new RefElement(stampedId,
                                  scopedResolver.resolveMap(template.spec(), stampedId),
                                  null, null, template.refs());
        }

        @Override
        public ForEachDirective getForEach(RefElement element) {return element.forEach();}

        @Override
        public String getId(RefElement element) {return element.id();}

        @Override
        public String getWhen(RefElement element) {return element.when();}

        @Override
        public List<ForEachAdapter.Reference> getReferences(RefElement element) {return element.refs();}

        @Override
        public RefElement withReferences(RefElement element, List<ForEachAdapter.Reference> rewritten) {
            return new RefElement(element.id(), element.spec(), element.forEach(),
                                  element.when(), rewritten);
        }
    }

    @Test
    void reference_rewriting_static_unchanged() {
        var refAdapter = new RefAdapter();
        var elements   = new LinkedHashMap<String, RefElement>();
        elements.put("static-node", new RefElement("static-node",
                                                   Map.of(), null, null, List.of()));
        elements.put("consumer", new RefElement("consumer",
                                                Map.of(), null, null,
                                                List.of(new ForEachAdapter.Reference("static-node", false))));

        var result = ForEachExpander.expand(elements, Map.of(),
                                            resolver, refAdapter, 1000);

        RefElement consumer = result.elements().get("consumer");
        assertThat(consumer.refs()).containsExactly(
                new ForEachAdapter.Reference("static-node", false));
    }

    @Test
    void reference_rewriting_same_group_paired() {
        var refAdapter = new RefAdapter();
        var groups = Map.of("regional",
                            new IterationGroup("region", List.of("us", "eu")));
        var elements = new LinkedHashMap<String, RefElement>();
        elements.put("source", new RefElement("source",
                                              Map.of(), new ForEachDirective.GroupRef("regional"), null, List.of()));
        elements.put("sink", new RefElement("sink",
                                            Map.of(), new ForEachDirective.GroupRef("regional"), null,
                                            List.of(new ForEachAdapter.Reference("source", false))));

        var result = ForEachExpander.expand(elements, groups,
                                            resolver, refAdapter, 1000);

        RefElement sinkUs = result.elements().get("sink.us");
        assertThat(sinkUs.refs()).containsExactly(
                new ForEachAdapter.Reference("source.us", false));
        RefElement sinkEu = result.elements().get("sink.eu");
        assertThat(sinkEu.refs()).containsExactly(
                new ForEachAdapter.Reference("source.eu", false));
    }

    @Test
    void reference_rewriting_cross_group_optional_skipped() {
        var refAdapter = new RefAdapter();
        var groups = Map.of(
                "g1", new IterationGroup("a", List.of("x")),
                "g2", new IterationGroup("b", List.of("y")));
        var elements = new LinkedHashMap<String, RefElement>();
        elements.put("src", new RefElement("src",
                                           Map.of(), new ForEachDirective.GroupRef("g1"), null, List.of()));
        elements.put("sink", new RefElement("sink",
                                            Map.of(), new ForEachDirective.GroupRef("g2"), null,
                                            List.of(new ForEachAdapter.Reference("src", true))));

        var result = ForEachExpander.expand(elements, groups,
                                            resolver, refAdapter, 1000);

        RefElement sinkY = result.elements().get("sink.y");
        assertThat(sinkY.refs()).isEmpty();
    }

    @Test
    void reference_rewriting_cross_group_required_throws() {
        var refAdapter = new RefAdapter();
        var groups = Map.of(
                "g1", new IterationGroup("a", List.of("x")),
                "g2", new IterationGroup("b", List.of("y")));
        var elements = new LinkedHashMap<String, RefElement>();
        elements.put("src", new RefElement("src",
                                           Map.of(), new ForEachDirective.GroupRef("g1"), null, List.of()));
        elements.put("sink", new RefElement("sink",
                                            Map.of(), new ForEachDirective.GroupRef("g2"), null,
                                            List.of(new ForEachAdapter.Reference("src", false))));

        assertThatThrownBy(() -> ForEachExpander.expand(elements, groups,
                                                        resolver, refAdapter, 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different group");
    }

    @Test
    void reference_to_excluded_required_throws() {
        var refAdapter = new RefAdapter();
        var elements   = new LinkedHashMap<String, RefElement>();
        elements.put("excluded", new RefElement("excluded",
                                                Map.of(), null, "false", List.of()));
        elements.put("consumer", new RefElement("consumer",
                                                Map.of(), null, null,
                                                List.of(new ForEachAdapter.Reference("excluded", false))));

        assertThatThrownBy(() -> ForEachExpander.expand(elements, Map.of(),
                                                        resolver, refAdapter, 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("excluded");
    }

    @Test
    void no_references_default_noop() {
        var elements = new LinkedHashMap<String, TestElement>();
        elements.put("node", new TestElement("node",
                                             Map.of("k", "v"), null, null));

        var result = ForEachExpander.expand(elements, Map.of(),
                                            resolver, adapter, 1000);

        assertThat(result.elements()).containsKey("node");
    }
}
