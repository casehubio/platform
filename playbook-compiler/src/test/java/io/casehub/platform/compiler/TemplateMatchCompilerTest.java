package io.casehub.platform.compiler;

import io.casehub.template.registry.TemplateSchema;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class TemplateMatchCompilerTest {

    private TemplateSchemaResolver resolver(String ns, List<TemplateSchema> schemas) {
        return namespace -> namespace.equals(ns) ? schemas : List.of();
    }

    @Test
    void templateMode_compilesSuccessfully() {
        var schemas = List.of(
            new TemplateSchema("ns", "vote", List.of("I ${decision} this"),
                Map.of("decision", VariableConstraint.enumOf("approve", "reject")),
                List.of()));
        var compiler = new PlaybookCompiler(null, resolver("ns", schemas));

        Map<String, Object> matchConfig = new LinkedHashMap<>();
        matchConfig.put("input", "${text}");
        matchConfig.put("namespace", "ns");
        matchConfig.put("on", Map.of("vote", List.of(Map.of("log", "voted"))));

        var result = compiler.compile("test", Map.of(
            "steps", List.of(Map.of("match", matchConfig))
        ));
        assertInstanceOf(CompilationOutcome.Success.class, result);
    }

    @Test
    void templateMode_unknownNamespace_producesError() {
        var compiler = new PlaybookCompiler(null, TemplateSchemaResolver.noOp());

        Map<String, Object> matchConfig = new LinkedHashMap<>();
        matchConfig.put("input", "${text}");
        matchConfig.put("namespace", "unknown");
        matchConfig.put("on", Map.of("t1", List.of(Map.of("log", "x"))));

        var result = compiler.compile("test", Map.of(
            "steps", List.of(Map.of("match", matchConfig))
        ));
        assertInstanceOf(CompilationOutcome.Failure.class, result);
        var failure = (CompilationOutcome.Failure) result;
        assertThat(failure.diagnostics()).anyMatch(d ->
            d.message().contains("unknown") && d.severity() == CompilationDiagnostic.Severity.ERROR);
    }

    @Test
    void templateMode_unknownHandler_producesError() {
        var schemas = List.of(
            new TemplateSchema("ns", "vote", List.of("pattern"),
                Map.of("decision", VariableConstraint.string()), List.of()));
        var compiler = new PlaybookCompiler(null, resolver("ns", schemas));

        Map<String, Object> matchConfig = new LinkedHashMap<>();
        matchConfig.put("input", "${text}");
        matchConfig.put("namespace", "ns");
        matchConfig.put("on", Map.of("nonexistent", List.of(Map.of("log", "x"))));

        var result = compiler.compile("test", Map.of(
            "steps", List.of(Map.of("match", matchConfig))
        ));
        assertInstanceOf(CompilationOutcome.Failure.class, result);
        var failure = (CompilationOutcome.Failure) result;
        assertThat(failure.diagnostics()).anyMatch(d ->
            d.message().contains("nonexistent"));
    }

    @Test
    void templateMode_undeclaredVariable_producesError() {
        var schemas = List.of(
            new TemplateSchema("ns", "vote", List.of("I ${decision} this"),
                Map.of("decision", VariableConstraint.string()), List.of()));
        var compiler = new PlaybookCompiler(null, resolver("ns", schemas));

        Map<String, Object> matchConfig = new LinkedHashMap<>();
        matchConfig.put("input", "${text}");
        matchConfig.put("namespace", "ns");
        matchConfig.put("on", Map.of("vote", List.of(
            Map.of("log", "${vote.nonexistent}"))));

        var result = compiler.compile("test", Map.of(
            "steps", List.of(Map.of("match", matchConfig))
        ));
        assertInstanceOf(CompilationOutcome.Failure.class, result);
        var failure = (CompilationOutcome.Failure) result;
        assertThat(failure.diagnostics()).anyMatch(d ->
            d.message().contains("nonexistent") && d.severity() == CompilationDiagnostic.Severity.ERROR);
    }

    @Test
    void templateMode_optionalVariable_producesWarning() {
        var schemas = List.of(
            new TemplateSchema("ns", "vote", List.of("I ${decision} this"),
                Map.of("decision", VariableConstraint.string(),
                       "rationale", VariableConstraint.string().optional()),
                List.of()));
        var compiler = new PlaybookCompiler(null, resolver("ns", schemas));

        Map<String, Object> matchConfig = new LinkedHashMap<>();
        matchConfig.put("input", "${text}");
        matchConfig.put("namespace", "ns");
        matchConfig.put("on", Map.of("vote", List.of(
            Map.of("log", "${vote.rationale}"))));

        var result = compiler.compile("test", Map.of(
            "steps", List.of(Map.of("match", matchConfig))
        ));
        assertInstanceOf(CompilationOutcome.Success.class, result);
        var success = (CompilationOutcome.Success) result;
        assertThat(success.result()).isNotNull();
    }

    @Test
    void templateMode_crossHandlerReference_producesError() {
        var schemas = List.of(
            new TemplateSchema("ns", "vote", List.of("I ${decision} this"),
                Map.of("decision", VariableConstraint.string()), List.of()),
            new TemplateSchema("ns", "escalate", List.of("escalate to ${target}"),
                Map.of("target", VariableConstraint.string()), List.of()));
        var compiler = new PlaybookCompiler(null, resolver("ns", schemas));

        Map<String, Object> matchConfig = new LinkedHashMap<>();
        matchConfig.put("input", "${text}");
        matchConfig.put("namespace", "ns");
        Map<String, Object> onHandlers = new LinkedHashMap<>();
        onHandlers.put("vote", List.of(Map.of("log", "${escalate.target}")));
        onHandlers.put("escalate", List.of(Map.of("log", "ok")));
        matchConfig.put("on", onHandlers);

        var result = compiler.compile("test", Map.of(
            "steps", List.of(Map.of("match", matchConfig))
        ));
        assertInstanceOf(CompilationOutcome.Failure.class, result);
        var failure = (CompilationOutcome.Failure) result;
        assertThat(failure.diagnostics()).anyMatch(d ->
            d.message().contains("Cross-handler"));
    }

    @Test
    void templateMode_missingInput_producesError() {
        var schemas = List.of(
            new TemplateSchema("ns", "vote", List.of("pattern"),
                Map.of("decision", VariableConstraint.string()), List.of()));
        var compiler = new PlaybookCompiler(null, resolver("ns", schemas));

        Map<String, Object> matchConfig = new LinkedHashMap<>();
        matchConfig.put("namespace", "ns");
        matchConfig.put("on", Map.of("vote", List.of(Map.of("log", "x"))));

        var result = compiler.compile("test", Map.of(
            "steps", List.of(Map.of("match", matchConfig))
        ));
        assertInstanceOf(CompilationOutcome.Failure.class, result);
        var failure = (CompilationOutcome.Failure) result;
        assertThat(failure.diagnostics()).anyMatch(d ->
            d.message().contains("input") && d.severity() == CompilationDiagnostic.Severity.ERROR);
    }

    @Test
    void templateMode_noOp_resolver_default() {
        var compiler = new PlaybookCompiler();
        assertNotNull(compiler.schemaResolver());
        assertTrue(compiler.schemaResolver().resolve("any").isEmpty());
    }

    @Test
    void valueMode_match_unaffected() {
        var compiler = new PlaybookCompiler();
        var result = compiler.compile("test", Map.of(
            "steps", List.of(Map.of(
                "match", "status",
                "cases", List.of(Map.of("pattern", "active"))
            ))
        ));
        assertInstanceOf(CompilationOutcome.Success.class, result);
    }
}
