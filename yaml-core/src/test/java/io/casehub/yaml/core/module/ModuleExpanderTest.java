package io.casehub.yaml.core.module;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleExpanderTest {

    @Test
    void alias_prefixes_section_keys() {
        var module = new YamlModule("monitor", Map.of(), Map.of(),
                Map.of("nodes", Map.of("cpu-check", Map.of("type", "sensor"))));
        var imp = new YamlImport("monitor", "infra", null, Map.of());
        var result = ModuleExpander.expand(List.of(imp),
                Map.of("monitor", module), Map.of());
        assertThat(result.sections().get("nodes")).containsKey("infra.cpu-check");
    }

    @Test
    void parameter_resolution_builds_scope() {
        var param = new YamlModuleParameter(ParameterType.STRING, true, null,
                null, null, null, null, null, List.of(), null);
        var module = new YamlModule("monitor", Map.of("threshold", param), Map.of(),
                Map.of("nodes", Map.of("check", Map.of("val", "x"))));
        var imp = new YamlImport("monitor", "mon", null, Map.of("threshold", "90"));
        var result = ModuleExpander.expand(List.of(imp),
                Map.of("monitor", module), Map.of());
        assertThat(result.moduleScopes().get("mon"))
                .containsEntry("threshold", "90");
    }

    @Test
    void default_parameter_used_when_not_provided() {
        var param = new YamlModuleParameter(ParameterType.STRING, false, "us-east",
                null, null, null, null, null, List.of(), null);
        var module = new YamlModule("m", Map.of("region", param), Map.of(),
                Map.of("nodes", Map.of("n", Map.of())));
        var imp = new YamlImport("m", "a", null, Map.of());
        var result = ModuleExpander.expand(List.of(imp),
                Map.of("m", module), Map.of());
        assertThat(result.moduleScopes().get("a"))
                .containsEntry("region", "us-east");
    }

    @Test
    void multiple_imports_merge_sections() {
        var m1 = new YamlModule("a", Map.of(), Map.of(),
                Map.of("nodes", Map.of("n1", Map.of("t", "1"))));
        var m2 = new YamlModule("b", Map.of(), Map.of(),
                Map.of("nodes", Map.of("n2", Map.of("t", "2"))));
        var result = ModuleExpander.expand(
                List.of(new YamlImport("a", "x", null, Map.of()),
                        new YamlImport("b", "y", null, Map.of())),
                Map.of("a", m1, "b", m2), Map.of());
        assertThat(result.sections().get("nodes"))
                .containsKey("x.n1")
                .containsKey("y.n2");
    }

    @Test
    void existing_sections_preserved() {
        var module = new YamlModule("m", Map.of(), Map.of(),
                Map.of("nodes", Map.of("new", Map.of())));
        var existing = Map.<String, Map<String, Object>>of(
                "nodes", Map.of("existing", Map.of("type", "fixed")));
        var result = ModuleExpander.expand(
                List.of(new YamlImport("m", "a", null, Map.of())),
                Map.of("m", module), existing);
        assertThat(result.sections().get("nodes"))
                .containsKey("existing")
                .containsKey("a.new");
    }

    @Test
    void conditional_import_returns_importConditions() {
        var module = new YamlModule("m", Map.of(), Map.of(),
                Map.of("nodes", Map.of("n", Map.of())));
        var imp = new YamlImport("m", "a", "${var.enabled}", Map.of());
        var result = ModuleExpander.expand(List.of(imp),
                Map.of("m", module), Map.of());
        assertThat(result.importConditions()).containsEntry("a", "${var.enabled}");
    }

    @Test
    void unconditional_import_absent_from_importConditions() {
        var module = new YamlModule("m", Map.of(), Map.of(),
                                    Map.of("nodes", Map.of("n", Map.of())));
        var imp = new YamlImport("m", "a", null, Map.of());
        var result = ModuleExpander.expand(List.of(imp),
                                           Map.of("m", module), Map.of());
        assertThat(result.importConditions()).doesNotContainKey("a");
    }

    @Test
    void unknown_module_throws() {
        var imp = new YamlImport("nonexistent", "a", null, Map.of());
        assertThatThrownBy(() -> ModuleExpander.expand(
                List.of(imp), Map.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void missing_alias_throws() {
        var module = new YamlModule("m", Map.of(), Map.of(), Map.of());
        var imp = new YamlImport("m", null, null, Map.of());
        assertThatThrownBy(() -> ModuleExpander.expand(
                List.of(imp), Map.of("m", module), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alias");
    }

    @Test
    void blank_alias_throws() {
        var module = new YamlModule("m", Map.of(), Map.of(), Map.of());
        var imp = new YamlImport("m", "  ", null, Map.of());
        assertThatThrownBy(() -> ModuleExpander.expand(
                List.of(imp), Map.of("m", module), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alias");
    }

    @Test
    void dot_in_alias_throws() {
        var module = new YamlModule("m", Map.of(), Map.of(), Map.of());
        var imp = new YamlImport("m", "infra.monitor", null, Map.of());
        assertThatThrownBy(() -> ModuleExpander.expand(
                List.of(imp), Map.of("m", module), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".");
    }

    @Test
    void duplicate_alias_throws() {
        var module = new YamlModule("m", Map.of(), Map.of(),
                Map.of("nodes", Map.of("n", Map.of())));
        assertThatThrownBy(() -> ModuleExpander.expand(
                List.of(new YamlImport("m", "a", null, Map.of()),
                        new YamlImport("m", "a", null, Map.of())),
                Map.of("m", module), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate")
                .hasMessageContaining("a");
    }

    @Test
    void unknown_parameter_throws() {
        var module = new YamlModule("m",
                Map.of("region", new YamlModuleParameter(ParameterType.STRING,
                        false, null, null, null, null, null, null, List.of(), null)),
                Map.of(), Map.of("nodes", Map.of("n", Map.of())));
        var imp = new YamlImport("m", "a", null, Map.of("reigon", "us-east"));
        assertThatThrownBy(() -> ModuleExpander.expand(
                List.of(imp), Map.of("m", module), Map.of()))
                .isInstanceOf(ParameterValidationException.class);
    }

    @Test
    void empty_imports_returns_existing_sections() {
        var existing = Map.<String, Map<String, Object>>of(
                "nodes", Map.of("db", Map.of("type", "database")));
        var result = ModuleExpander.expand(List.of(), Map.of(), existing);
        assertThat(result.sections()).containsKey("nodes");
        assertThat(result.sections().get("nodes")).containsKey("db");
        assertThat(result.moduleScopes()).isEmpty();
        assertThat(result.importConditions()).isEmpty();
    }

// --- SectionDeserializer + SectionContentRewriter ---

    record TypedNode(String type, Map<String, Object> spec, List<String> dependsOn) {}

    @Test
    void deserializer_converts_during_expansion() {
        var module = new YamlModule("m", Map.of(), Map.of(),
                                    Map.of("nodes", Map.of("check", Map.of("type", "sensor",
                                                                           "spec", Map.of("uri", "s3://data"),
                                                                           "dependsOn", List.of()))));
        var imp = new YamlImport("m", "a", null, Map.of());

        SectionDeserializer deserializer = (section, key, raw) -> {
            if ("nodes".equals(section)) {
                return new TypedNode(
                        (String) raw.get("type"),
                        raw.get("spec") instanceof Map ? (Map<String, Object>) raw.get("spec") : Map.of(),
                        raw.get("dependsOn") instanceof List ? ((List<?>) raw.get("dependsOn")).stream()
                                                                                               .map(Object::toString).toList() : List.of());
            }
            return raw;
        };

        var result = ModuleExpander.expand(List.of(imp),
                                           Map.of("m", module), Map.of(), ExpansionOptions.of(deserializer, null));

        Object value = result.sections().get("nodes").get("a.check");
        assertThat(value).isInstanceOf(TypedNode.class);
        assertThat(((TypedNode) value).type()).isEqualTo("sensor");
    }

    @Test
    void deserializer_null_passes_raw() {
        var module = new YamlModule("m", Map.of(), Map.of(),
                                    Map.of("nodes", Map.of("n", Map.of("type", "x"))));
        var imp = new YamlImport("m", "a", null, Map.of());
        var result = ModuleExpander.expand(List.of(imp),
                                           Map.of("m", module), Map.of(), ExpansionOptions.NONE);
        assertThat(result.sections().get("nodes").get("a.n")).isInstanceOf(Map.class);
    }

    @Test
    void rewriter_receives_typed_objects() {
        var module = new YamlModule("m", Map.of(), Map.of(),
                                    Map.of("nodes", Map.of("alerter", Map.of("type", "alert",
                                                                             "spec", Map.of(), "dependsOn", List.of("monitor")),
                                                           "monitor", Map.of("type", "sensor",
                                                                             "spec", Map.of(), "dependsOn", List.of()))));
        var imp = new YamlImport("m", "pipe", null, Map.of());

        SectionDeserializer deserializer = (section, key, raw) ->
                                                   new TypedNode((String) raw.get("type"),
                                                                 raw.get("spec") instanceof Map ? (Map<String, Object>) raw.get("spec") : Map.of(),
                                                                 raw.get("dependsOn") instanceof List ? ((List<?>) raw.get("dependsOn")).stream()
                                                                                                                                        .map(Object::toString).toList() : List.of());

        SectionContentRewriter rewriter = (section, key, value, alias, moduleKeys) -> {
            if (value instanceof TypedNode node) {
                List<String> rewritten = node.dependsOn().stream()
                                             .map(dep -> moduleKeys.contains(dep) ? alias + "." + dep : dep)
                                             .toList();
                return new TypedNode(node.type(), node.spec(), rewritten);
            }
            return value;
        };

        var result = ModuleExpander.expand(List.of(imp),
                                           Map.of("m", module), Map.of(), ExpansionOptions.of(deserializer, rewriter));

        TypedNode alerter = (TypedNode) result.sections().get("nodes").get("pipe.alerter");
        assertThat(alerter.dependsOn()).containsExactly("pipe.monitor");
    }

// --- Typed accessor ---

    @Test
    void section_typed_accessor() {
        var module = new YamlModule("m", Map.of(), Map.of(),
                                    Map.of("nodes", Map.of("n", Map.of("type", "x",
                                                                       "spec", Map.of(), "dependsOn", List.of()))));
        var imp = new YamlImport("m", "a", null, Map.of());

        SectionDeserializer deserializer = (section, key, raw) ->
                                                   new TypedNode((String) raw.get("type"),
                                                                 raw.get("spec") instanceof Map ? (Map<String, Object>) raw.get("spec") : Map.of(),
                                                                 List.of());

        var result = ModuleExpander.expand(List.of(imp),
                                           Map.of("m", module), Map.of(), ExpansionOptions.of(deserializer, null));

        Map<String, TypedNode> nodes = result.typedSection("nodes");
        assertThat(nodes.get("a.n").type()).isEqualTo("x");
    }

    @Test
    void section_accessor_returns_empty_for_unknown() {
        var                 result  = ModuleExpander.expand(List.of(), Map.of(), Map.of(), ExpansionOptions.NONE);
        Map<String, Object> unknown = result.section("nonexistent");
        assertThat(unknown).isEmpty();
    }

    @Test
    void typedSection_returns_empty_for_unknown() {
        var                    result  = ModuleExpander.expand(List.of(), Map.of(), Map.of(), ExpansionOptions.NONE);
        Map<String, TypedNode> unknown = result.typedSection("nonexistent");
        assertThat(unknown).isEmpty();
    }

// --- Structural type checking ---

    @Test
    void missing_output_reference_throws() {
        var output = new YamlModuleOutput(ParameterType.STRING, "val");
        var module = new YamlModule("m", Map.of(), Map.of("real", output), Map.of());
        var paramDecl = new YamlModuleParameter(ParameterType.STRING, true, null,
                                                null, null, null, null, null, List.of(), null);
        var consumer = new YamlModule("c", Map.of("x", paramDecl), Map.of(), Map.of());

        assertThatThrownBy(() -> ModuleExpander.expand(
                List.of(new YamlImport("m", "a", null, Map.of()),
                        new YamlImport("c", "b", null,
                                       Map.of("x", "${module.a.nonexistent}"))),
                Map.of("m", module, "c", consumer), Map.of()))
                .isInstanceOf(ParameterValidationException.class)
                .satisfies(ex -> {
                    var violations = ((ParameterValidationException) ex).violations();
                    assertThat(violations).hasSize(1);
                    assertThat(violations.get(0).constraint())
                            .isEqualTo("module-ref-missing-output");
                    assertThat(violations.get(0).message())
                            .contains("nonexistent")
                            .contains("real");
                });
    }

    @Test
    void type_incompatible_whole_value_throws() {
        var boolOutput = new YamlModuleOutput(ParameterType.BOOLEAN, "${var.flag}");
        var boolParam = new YamlModuleParameter(ParameterType.BOOLEAN, true, null,
                                                null, null, null, null, null, List.of(), null);
        var producer = new YamlModule("producer",
                                      Map.of("flag", boolParam),
                                      Map.of("enabled", boolOutput), Map.of());

        var intParam = new YamlModuleParameter(ParameterType.INTEGER, true, null,
                                               null, null, null, null, null, List.of(), null);
        var consumer = new YamlModule("consumer",
                                      Map.of("count", intParam), Map.of(), Map.of());

        assertThatThrownBy(() -> ModuleExpander.expand(
                List.of(new YamlImport("producer", "p", null,
                                       Map.of("flag", "true")),
                        new YamlImport("consumer", "c", null,
                                       Map.of("count", "${module.p.enabled}"))),
                Map.of("producer", producer, "consumer", consumer), Map.of()))
                .isInstanceOf(ParameterValidationException.class)
                .satisfies(ex -> {
                    var violations = ((ParameterValidationException) ex).violations();
                    assertThat(violations).hasSize(1);
                    assertThat(violations.get(0).constraint())
                            .isEqualTo("module-ref-type-incompatible");
                    assertThat(violations.get(0).message())
                            .contains("BOOLEAN")
                            .contains("INTEGER");
                });
    }

    @Test
    void type_compatible_widening_passes() {
        var intOutput = new YamlModuleOutput(ParameterType.INTEGER, "${var.port}");
        var intParam = new YamlModuleParameter(ParameterType.INTEGER, true, null,
                                               null, null, null, null, null, List.of(), null);
        var producer = new YamlModule("producer",
                                      Map.of("port", intParam),
                                      Map.of("port", intOutput), Map.of());

        var numParam = new YamlModuleParameter(ParameterType.NUMBER, true, null,
                                               null, null, null, null, null, List.of(), null);
        var consumer = new YamlModule("consumer",
                                      Map.of("factor", numParam), Map.of(), Map.of());

        var result = ModuleExpander.expand(
                List.of(new YamlImport("producer", "p", null,
                                       Map.of("port", "5432")),
                        new YamlImport("consumer", "c", null,
                                       Map.of("factor", "${module.p.port}"))),
                Map.of("producer", producer, "consumer", consumer), Map.of());

        assertThat(result.moduleScopes().get("c"))
                .containsEntry("factor", "5432");
    }

    @Test
    void embedded_ref_non_string_param_throws() {
        var strOutput = new YamlModuleOutput(ParameterType.STRING, "val");
        var producer = new YamlModule("producer", Map.of(),
                                      Map.of("host", strOutput), Map.of());

        var intParam = new YamlModuleParameter(ParameterType.INTEGER, true, null,
                                               null, null, null, null, null, List.of(), null);
        var consumer = new YamlModule("consumer",
                                      Map.of("port", intParam), Map.of(), Map.of());

        assertThatThrownBy(() -> ModuleExpander.expand(
                List.of(new YamlImport("producer", "p", null, Map.of()),
                        new YamlImport("consumer", "c", null,
                                       Map.of("port", "prefix-${module.p.host}"))),
                Map.of("producer", producer, "consumer", consumer), Map.of()))
                .isInstanceOf(ParameterValidationException.class)
                .satisfies(ex -> {
                    var violations = ((ParameterValidationException) ex).violations();
                    assertThat(violations).hasSize(1);
                    assertThat(violations.get(0).constraint())
                            .isEqualTo("module-ref-embedded-type");
                    assertThat(violations.get(0).message())
                            .contains("INTEGER")
                            .contains("string interpolation");
                });
    }

    @Test
    void embedded_ref_string_param_passes() {
        var strOutput = new YamlModuleOutput(ParameterType.STRING, "localhost");
        var producer = new YamlModule("producer", Map.of(),
                                      Map.of("host", strOutput), Map.of());

        var strParam = new YamlModuleParameter(ParameterType.STRING, true, null,
                                               null, null, null, null, null, List.of(), null);
        var consumer = new YamlModule("consumer",
                                      Map.of("url", strParam), Map.of(), Map.of());

        var result = ModuleExpander.expand(
                List.of(new YamlImport("producer", "p", null, Map.of()),
                        new YamlImport("consumer", "c", null,
                                       Map.of("url", "http://${module.p.host}:8080"))),
                Map.of("producer", producer, "consumer", consumer), Map.of());

        assertThat(result.moduleScopes().get("c"))
                .containsEntry("url", "http://localhost:8080");
    }

    @Test
    void collect_all_multiple_errors() {
        var boolOutput = new YamlModuleOutput(ParameterType.BOOLEAN, "true");
        var producer = new YamlModule("producer", Map.of(),
                                      Map.of("flag", boolOutput), Map.of());

        var intParam = new YamlModuleParameter(ParameterType.INTEGER, true, null,
                                               null, null, null, null, null, List.of(), null);
        var strParam = new YamlModuleParameter(ParameterType.STRING, true, null,
                                               null, null, null, null, null, List.of(), null);
        var consumer = new YamlModule("consumer",
                                      Map.of("count", intParam, "name", strParam), Map.of(), Map.of());

        assertThatThrownBy(() -> ModuleExpander.expand(
                List.of(new YamlImport("producer", "p", null, Map.of()),
                        new YamlImport("consumer", "c", null,
                                       Map.of("count", "${module.p.flag}",
                                              "name", "${module.p.missing}"))),
                Map.of("producer", producer, "consumer", consumer), Map.of()))
                .isInstanceOf(ParameterValidationException.class)
                .satisfies(ex -> {
                    var violations = ((ParameterValidationException) ex).violations();
                    assertThat(violations).hasSizeGreaterThanOrEqualTo(2);
                    assertThat(violations.stream().map(ParameterViolation::constraint))
                            .contains("module-ref-type-incompatible",
                                      "module-ref-missing-output");
                });
    }

    @Test
    void list_to_string_rejected() {
        var listOutput = new YamlModuleOutput(ParameterType.LIST, "${var.items}");
        var listParam = new YamlModuleParameter(ParameterType.LIST, true, null,
                                                null, null, null, null, null, List.of(), null);
        var producer = new YamlModule("producer",
                                      Map.of("items", listParam),
                                      Map.of("items", listOutput), Map.of());

        var strParam = new YamlModuleParameter(ParameterType.STRING, true, null,
                                               null, null, null, null, null, List.of(), null);
        var consumer = new YamlModule("consumer",
                                      Map.of("label", strParam), Map.of(), Map.of());

        assertThatThrownBy(() -> ModuleExpander.expand(
                List.of(new YamlImport("producer", "p", null,
                                       Map.of("items", "a,b,c")),
                        new YamlImport("consumer", "c", null,
                                       Map.of("label", "${module.p.items}"))),
                Map.of("producer", producer, "consumer", consumer), Map.of()))
                .isInstanceOf(ParameterValidationException.class)
                .satisfies(ex -> {
                    var violations = ((ParameterValidationException) ex).violations();
                    assertThat(violations).hasSize(1);
                    assertThat(violations.get(0).constraint())
                            .isEqualTo("module-ref-type-incompatible");
                });
    }


    // --- Chaining ---

    @Test
    void chaining_later_import_uses_earlier_output() {
        var dbParam = new YamlModuleParameter(ParameterType.STRING, true, null,
                null, null, null, null, null, List.of(), null);
        var dbOutput = new YamlModuleOutput(ParameterType.STRING,
                "jdbc:${var.engine}://db:5432/app");
        var dbModule = new YamlModule("database", Map.of("engine", dbParam),
                Map.of("url", dbOutput), Map.of("nodes", Map.of("db", Map.of())));

        var cacheParam = new YamlModuleParameter(ParameterType.STRING, true, null,
                null, null, null, null, null, List.of(), null);
        var cacheModule = new YamlModule("cache", Map.of("backend", cacheParam),
                Map.of(), Map.of("nodes", Map.of("c", Map.of())));

        var result = ModuleExpander.expand(
                List.of(new YamlImport("database", "app-db", null,
                                Map.of("engine", "postgres")),
                        new YamlImport("cache", "app-cache", null,
                                Map.of("backend", "${module.app-db.url}"))),
                Map.of("database", dbModule, "cache", cacheModule),
                Map.of());

        assertThat(result.moduleScopes().get("app-cache"))
                .containsEntry("backend", "jdbc:postgres://db:5432/app");
    }

    @Test
    void forward_reference_throws_actionable_error() {
        var module = new YamlModule("m", Map.of(),
                Map.of("out", new YamlModuleOutput(ParameterType.STRING, "val")),
                Map.of());

        assertThatThrownBy(() -> ModuleExpander.expand(
                List.of(new YamlImport("m", "b", null,
                                Map.of("x", "${module.a.out}")),
                        new YamlImport("m", "a", null, Map.of())),
                Map.of("m", module), Map.of()))
                .isInstanceOf(ParameterValidationException.class)
                .satisfies(ex -> {
                    var violations = ((ParameterValidationException) ex).violations();
                    assertThat(violations).hasSize(1);
                    assertThat(violations.get(0).constraint())
                            .isEqualTo("module-ref-forward");
                    assertThat(violations.get(0).message())
                            .contains("b")
                            .contains("a");
                });
    }

    @Test
    void chaining_type_validated_after_resolution() {
        var dbParam = new YamlModuleParameter(ParameterType.INTEGER, true, null,
                null, null, null, null, null, List.of(), null);
        var dbOutput = new YamlModuleOutput(ParameterType.INTEGER, "${var.port}");
        var dbModule = new YamlModule("db", Map.of("port", dbParam),
                Map.of("port", dbOutput), Map.of());

        var appParam = new YamlModuleParameter(ParameterType.INTEGER, true, null,
                null, null, null, null, null, List.of(), null);
        var appModule = new YamlModule("app", Map.of("dbPort", appParam),
                Map.of(), Map.of());

        var result = ModuleExpander.expand(
                List.of(new YamlImport("db", "mydb", null, Map.of("port", "5432")),
                        new YamlImport("app", "myapp", null,
                                Map.of("dbPort", "${module.mydb.port}"))),
                Map.of("db", dbModule, "app", appModule),
                Map.of());

        assertThat(result.moduleScopes().get("myapp"))
                .containsEntry("dbPort", "5432");
    }

    @Test
    void conditional_import_outputs_available() {
        var output = new YamlModuleOutput(ParameterType.STRING, "value");
        var module = new YamlModule("m", Map.of(),
                Map.of("out", output), Map.of());

        var result = ModuleExpander.expand(
                List.of(new YamlImport("m", "gated", "${var.enabled}", Map.of())),
                Map.of("m", module), Map.of());

        assertThat(result.moduleOutputs().get("gated"))
                .containsEntry("out", "value");
    }

    @Test
    void output_template_referencing_undeclared_parameter_throws() {
        var param = new YamlModuleParameter(ParameterType.STRING, true, null,
                                            null, null, null, null, null, List.of(), null);
        var output = new YamlModuleOutput(ParameterType.STRING, "${var.host}:${var.nonexistent}");
        var module = new YamlModule("m", Map.of("host", param),
                                    Map.of("url", output), Map.of());

        assertThatThrownBy(() -> ModuleExpander.expand(
                List.of(new YamlImport("m", "a", null, Map.of("host", "localhost"))),
                Map.of("m", module), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("undeclared parameter")
                .hasMessageContaining("nonexistent")
                .hasMessageContaining("host");
    }

    @Test
    void output_template_referencing_declared_parameter_passes() {
        var param = new YamlModuleParameter(ParameterType.STRING, true, null,
                                            null, null, null, null, null, List.of(), null);
        var output = new YamlModuleOutput(ParameterType.STRING, "${var.host}:8080");
        var module = new YamlModule("m", Map.of("host", param),
                                    Map.of("url", output), Map.of());

        var result = ModuleExpander.expand(
                List.of(new YamlImport("m", "a", null, Map.of("host", "localhost"))),
                Map.of("m", module), Map.of());

        assertThat(result.moduleOutputs().get("a")).containsEntry("url", "localhost:8080");
    }


// --- resolveExtensions ---

    @Test
    void resolve_no_extensions_converts_all() {
        var h1 = new YamlModuleFile.YamlModuleHeader("a", Map.of(), Map.of(), null);
        var h2 = new YamlModuleFile.YamlModuleHeader("b", Map.of(), Map.of(), null);
        var f1 = new YamlModuleFile(h1, Map.of("nodes", Map.of("n1", Map.of())), List.of());
        var f2 = new YamlModuleFile(h2, Map.of("nodes", Map.of("n2", Map.of())), List.of());

        var resolved = ModuleExpander.resolveExtensions(List.of(f1, f2));

        assertThat(resolved).containsKeys("a", "b");
        assertThat(resolved.get("a").sections().get("nodes")).containsKey("n1");
        assertThat(resolved.get("b").sections().get("nodes")).containsKey("n2");
    }

    @Test
    void resolve_inherits_parameters() {
        var parentParam = YamlModuleParameter.builder().type(ParameterType.STRING).required().build();
        var parentHeader = new YamlModuleFile.YamlModuleHeader("parent",
                                                               Map.of("region", parentParam), Map.of(), null);
        var parentFile = new YamlModuleFile(parentHeader, Map.of(), List.of());

        var childParam = YamlModuleParameter.builder().type(ParameterType.STRING).required().build();
        var childHeader = new YamlModuleFile.YamlModuleHeader("child",
                                                              Map.of("channel", childParam), Map.of(), "parent");
        var childFile = new YamlModuleFile(childHeader, Map.of(), List.of());

        var resolved = ModuleExpander.resolveExtensions(List.of(parentFile, childFile));

        assertThat(resolved.get("child").parameters())
                .containsKeys("region", "channel");
    }

    @Test
    void resolve_inherits_outputs() {
        var parentOutput = new YamlModuleOutput(ParameterType.STRING, "value");
        var parentHeader = new YamlModuleFile.YamlModuleHeader("parent",
                                                               Map.of(), Map.of("endpoint", parentOutput), null);
        var parentFile = new YamlModuleFile(parentHeader, Map.of(), List.of());

        var childHeader = new YamlModuleFile.YamlModuleHeader("child",
                                                              Map.of(), Map.of(), "parent");
        var childFile = new YamlModuleFile(childHeader, Map.of(), List.of());

        var resolved = ModuleExpander.resolveExtensions(List.of(parentFile, childFile));

        assertThat(resolved.get("child").outputs()).containsKey("endpoint");
    }

    @Test
    void resolve_inherits_sections() {
        var parentHeader = new YamlModuleFile.YamlModuleHeader("parent",
                                                               Map.of(), Map.of(), null);
        var parentFile = new YamlModuleFile(parentHeader,
                                            Map.of("nodes", Map.of("monitor", Map.of("type", "http-poller"))), List.of());

        var childHeader = new YamlModuleFile.YamlModuleHeader("child",
                                                              Map.of(), Map.of(), "parent");
        var childFile = new YamlModuleFile(childHeader, Map.of(), List.of());

        var resolved = ModuleExpander.resolveExtensions(List.of(parentFile, childFile));

        assertThat(resolved.get("child").sections().get("nodes"))
                .containsKey("monitor");
    }

    @Test
    void resolve_child_adds_section_entries() {
        var parentHeader = new YamlModuleFile.YamlModuleHeader("parent",
                                                               Map.of(), Map.of(), null);
        var parentFile = new YamlModuleFile(parentHeader,
                                            Map.of("nodes", Map.of("monitor", Map.of("type", "poller"))), List.of());

        var childHeader = new YamlModuleFile.YamlModuleHeader("child",
                                                              Map.of(), Map.of(), "parent");
        var childFile = new YamlModuleFile(childHeader,
                                           Map.of("nodes", Map.of("notifier", Map.of("type", "slack"))), List.of());

        var resolved = ModuleExpander.resolveExtensions(List.of(parentFile, childFile));

        assertThat(resolved.get("child").sections().get("nodes"))
                .containsKeys("monitor", "notifier");
    }

    @Test
    void resolve_child_adds_new_section() {
        var parentHeader = new YamlModuleFile.YamlModuleHeader("parent",
                                                               Map.of(), Map.of(), null);
        var parentFile = new YamlModuleFile(parentHeader,
                                            Map.of("nodes", Map.of("n", Map.of())), List.of());

        var childHeader = new YamlModuleFile.YamlModuleHeader("child",
                                                              Map.of(), Map.of(), "parent");
        var childFile = new YamlModuleFile(childHeader,
                                           Map.of("rules", Map.of("r", Map.of())), List.of());

        var resolved = ModuleExpander.resolveExtensions(List.of(parentFile, childFile));

        assertThat(resolved.get("child").sections()).containsKeys("nodes", "rules");
    }

    @Test
    void resolve_child_overrides_parameter() {
        var parentParam = YamlModuleParameter.builder().type(ParameterType.STRING)
                                             .defaultValue("us-east").build();
        var parentHeader = new YamlModuleFile.YamlModuleHeader("parent",
                                                               Map.of("region", parentParam), Map.of(), null);
        var parentFile = new YamlModuleFile(parentHeader, Map.of(), List.of());

        var childParam = YamlModuleParameter.builder().type(ParameterType.INTEGER)
                                            .required().build();
        var childHeader = new YamlModuleFile.YamlModuleHeader("child",
                                                              Map.of("region", childParam), Map.of(), "parent");
        var childFile = new YamlModuleFile(childHeader, Map.of(), List.of());

        var resolved = ModuleExpander.resolveExtensions(List.of(parentFile, childFile));

        assertThat(resolved.get("child").parameters().get("region").type())
                .isEqualTo(ParameterType.INTEGER);
        assertThat(resolved.get("child").parameters().get("region").required())
                .isTrue();
    }

    @Test
    void resolve_child_overrides_output() {
        var parentOutput = new YamlModuleOutput(ParameterType.STRING, "old");
        var parentHeader = new YamlModuleFile.YamlModuleHeader("parent",
                                                               Map.of(), Map.of("url", parentOutput), null);
        var parentFile = new YamlModuleFile(parentHeader, Map.of(), List.of());

        var childOutput = new YamlModuleOutput(ParameterType.INTEGER, "42");
        var childHeader = new YamlModuleFile.YamlModuleHeader("child",
                                                              Map.of(), Map.of("url", childOutput), "parent");
        var childFile = new YamlModuleFile(childHeader, Map.of(), List.of());

        var resolved = ModuleExpander.resolveExtensions(List.of(parentFile, childFile));

        assertThat(resolved.get("child").outputs().get("url").type())
                .isEqualTo(ParameterType.INTEGER);
    }

    @Test
    void resolve_child_overrides_section_entry() {
        var parentHeader = new YamlModuleFile.YamlModuleHeader("parent",
                                                               Map.of(), Map.of(), null);
        var parentFile = new YamlModuleFile(parentHeader,
                                            Map.of("nodes", Map.of("monitor",
                                                                   Map.of("type", "poller", "interval", 30))), List.of());

        var childHeader = new YamlModuleFile.YamlModuleHeader("child",
                                                              Map.of(), Map.of(), "parent");
        var childFile = new YamlModuleFile(childHeader,
                                           Map.of("nodes", Map.of("monitor",
                                                                  Map.of("type", "webhook"))), List.of());

        var resolved = ModuleExpander.resolveExtensions(List.of(parentFile, childFile));

        @SuppressWarnings("unchecked")
        Map<String, Object> monitor = (Map<String, Object>)
                                              resolved.get("child").sections().get("nodes").get("monitor");
        assertThat(monitor).containsEntry("type", "webhook");
        assertThat(monitor).doesNotContainKey("interval");
    }

    @Test
    void resolve_preserves_child_name() {
        var parentHeader = new YamlModuleFile.YamlModuleHeader("parent",
                                                               Map.of(), Map.of(), null);
        var parentFile = new YamlModuleFile(parentHeader, Map.of(), List.of());

        var childHeader = new YamlModuleFile.YamlModuleHeader("child",
                                                              Map.of(), Map.of(), "parent");
        var childFile = new YamlModuleFile(childHeader, Map.of(), List.of());

        var resolved = ModuleExpander.resolveExtensions(List.of(parentFile, childFile));

        assertThat(resolved).containsKey("child");
        assertThat(resolved.get("child").name()).isEqualTo("child");
    }

    @Test
    void resolve_unknown_parent_throws() {
        var childHeader = new YamlModuleFile.YamlModuleHeader("child",
                                                              Map.of(), Map.of(), "nonexistent");
        var childFile = new YamlModuleFile(childHeader, Map.of(), List.of());

        assertThatThrownBy(() -> ModuleExpander.resolveExtensions(List.of(childFile)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("child")
                .hasMessageContaining("nonexistent");
    }

    @Test
    void resolve_self_extension_throws() {
        var header = new YamlModuleFile.YamlModuleHeader("m",
                                                         Map.of(), Map.of(), "m");
        var file = new YamlModuleFile(header, Map.of(), List.of());

        assertThatThrownBy(() -> ModuleExpander.resolveExtensions(List.of(file)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extends itself");
    }

    @Test
    void resolve_chain_throws() {
        var grandparentHeader = new YamlModuleFile.YamlModuleHeader("gp",
                                                                    Map.of(), Map.of(), null);
        var grandparentFile = new YamlModuleFile(grandparentHeader, Map.of(), List.of());

        var parentHeader = new YamlModuleFile.YamlModuleHeader("parent",
                                                               Map.of(), Map.of(), "gp");
        var parentFile = new YamlModuleFile(parentHeader, Map.of(), List.of());

        var childHeader = new YamlModuleFile.YamlModuleHeader("child",
                                                              Map.of(), Map.of(), "parent");
        var childFile = new YamlModuleFile(childHeader, Map.of(), List.of());

        assertThatThrownBy(() -> ModuleExpander.resolveExtensions(
                List.of(grandparentFile, parentFile, childFile)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chain")
                .hasMessageContaining("child")
                .hasMessageContaining("parent");
    }

    @Test
    void resolve_duplicate_name_throws() {
        var h1 = new YamlModuleFile.YamlModuleHeader("m", Map.of(), Map.of(), null);
        var h2 = new YamlModuleFile.YamlModuleHeader("m", Map.of(), Map.of(), null);

        assertThatThrownBy(() -> ModuleExpander.resolveExtensions(
                List.of(new YamlModuleFile(h1, Map.of(), List.of()),
                        new YamlModuleFile(h2, Map.of(), List.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void resolve_mixed_extended_and_plain() {
        var parentHeader = new YamlModuleFile.YamlModuleHeader("parent",
                                                               Map.of(), Map.of(), null);
        var parentFile = new YamlModuleFile(parentHeader,
                                            Map.of("nodes", Map.of("base", Map.of())), List.of());

        var childHeader = new YamlModuleFile.YamlModuleHeader("child",
                                                              Map.of(), Map.of(), "parent");
        var childFile = new YamlModuleFile(childHeader,
                                           Map.of("nodes", Map.of("extra", Map.of())), List.of());

        var plainHeader = new YamlModuleFile.YamlModuleHeader("standalone",
                                                              Map.of(), Map.of(), null);
        var plainFile = new YamlModuleFile(plainHeader,
                                           Map.of("rules", Map.of("r1", Map.of())), List.of());

        var resolved = ModuleExpander.resolveExtensions(
                List.of(parentFile, childFile, plainFile));

        assertThat(resolved).hasSize(3);
        assertThat(resolved.get("child").sections().get("nodes"))
                .containsKeys("base", "extra");
        assertThat(resolved.get("standalone").sections()).containsKey("rules");
        assertThat(resolved.get("parent").sections().get("nodes"))
                .containsKey("base");
    }

    @Test
    void extended_module_expands_correctly() {
        var parentParam = YamlModuleParameter.builder().type(ParameterType.STRING).required().build();
        var parentHeader = new YamlModuleFile.YamlModuleHeader("monitoring",
                                                               Map.of("region", parentParam), Map.of(), null);
        var parentFile = new YamlModuleFile(parentHeader,
                                            Map.of("nodes", Map.of("monitor", Map.of("type", "poller"))), List.of());

        var childParam = YamlModuleParameter.builder().type(ParameterType.STRING).required().build();
        var childHeader = new YamlModuleFile.YamlModuleHeader("monitoring-slack",
                                                              Map.of("channel", childParam), Map.of(), "monitoring");
        var childFile = new YamlModuleFile(childHeader,
                                           Map.of("nodes", Map.of("notifier", Map.of("type", "slack"))), List.of());

        var modules = ModuleExpander.resolveExtensions(List.of(parentFile, childFile));

        var result = ModuleExpander.expand(
                List.of(new YamlImport("monitoring-slack", "alerts", null,
                                       Map.of("region", "us-east", "channel", "#ops"))),
                modules, Map.of());

        assertThat(result.sections().get("nodes"))
                .containsKey("alerts.monitor")
                .containsKey("alerts.notifier");
        assertThat(result.moduleScopes().get("alerts"))
                .containsEntry("region", "us-east")
                .containsEntry("channel", "#ops");
    }

    @Test
    void extended_module_with_bridge() {
        var parentParam = YamlModuleParameter.builder().type(ParameterType.STRING).required().build();
        var parentHeader = new YamlModuleFile.YamlModuleHeader("monitoring",
                                                               Map.of("region", parentParam), Map.of(), null);
        var parentFile = new YamlModuleFile(parentHeader,
                                            Map.of("nodes", Map.of("monitor", Map.of("type", "poller"))), List.of());

        var childParam = YamlModuleParameter.builder().type(ParameterType.STRING).required().build();
        var childHeader = new YamlModuleFile.YamlModuleHeader("monitoring-slack",
                                                              Map.of("channel", childParam), Map.of(), "monitoring");
        var childFile = new YamlModuleFile(childHeader,
                                           Map.of("nodes", Map.of("notifier", Map.of("type", "slack"))), List.of());

        var modules = ModuleExpander.resolveExtensions(List.of(parentFile, childFile));

        ModuleBridge<Map<String, Map<String, Object>>> identityBridge = new ModuleBridge<>() {
            @Override
            public Map<String, Map<String, Object>> fromSections(Map<String, Map<String, Object>> sections) {
                return sections;
            }

            @Override
            public Map<String, Map<String, Object>> toSections(Map<String, Map<String, Object>> content) {
                return content;
            }
        };

        var result = ModuleExpander.expand(
                List.of(new YamlImport("monitoring-slack", "alerts", null,
                                       Map.of("region", "us-east", "channel", "#ops"))),
                modules, Map.of(), identityBridge);

        assertThat(result.content().get("nodes"))
                .containsKey("alerts.monitor")
                .containsKey("alerts.notifier");
        assertThat(result.moduleScopes().get("alerts"))
                .containsEntry("region", "us-east")
                .containsEntry("channel", "#ops");
    }
}
