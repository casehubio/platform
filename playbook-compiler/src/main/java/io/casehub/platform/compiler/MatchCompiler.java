package io.casehub.platform.compiler;

import io.casehub.template.registry.TemplateSchema;
import io.casehub.yaml.core.resolver.VariableConstraint;
import io.serverlessworkflow.api.types.FlowDirective;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Set;
import io.serverlessworkflow.api.types.SetTask;
import io.serverlessworkflow.api.types.SetTaskConfiguration;
import io.serverlessworkflow.api.types.SwitchCase;
import io.serverlessworkflow.api.types.SwitchItem;
import io.serverlessworkflow.api.types.SwitchTask;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MatchCompiler implements StepCompiler {

    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("\\$\\{([^.}]+)\\.([^}]+)}");

    @Override
    @SuppressWarnings("unchecked")
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep, CompilationContext ctx) {
        if (stepValue instanceof Map<?,?> config && config.containsKey("namespace")) {
            return compileTemplateMode((Map<String, Object>) config, ctx);
        }
        return compileValueMode(stepKey, stepValue, fullStep, ctx);
    }

    @SuppressWarnings("unchecked")
    private TaskItem compileValueMode(String stepKey, Object stepValue, Map<String, Object> fullStep, CompilationContext ctx) {
        String name = ctx.generateName("switch");
        String matchExpr = String.valueOf(stepValue);

        var switchItems = new ArrayList<SwitchItem>();
        List<?> cases = (List<?>) fullStep.get("cases");
        if (cases != null) {
            for (int i = 0; i < cases.size(); i++) {
                var caseMap = (Map<String, Object>) cases.get(i);
                if (caseMap.containsKey("pattern")) {
                    String pattern = String.valueOf(caseMap.get("pattern"));
                    var switchCase = new SwitchCase()
                            .withWhen("${ " + matchExpr + " == \"" + pattern + "\" }")
                            .withThen(new FlowDirective().withFlowDirectiveEnum(FlowDirectiveEnum.CONTINUE));
                    switchItems.add(new SwitchItem("case_" + i, switchCase));
                } else if (caseMap.containsKey("default")) {
                    var switchCase = new SwitchCase()
                            .withThen(new FlowDirective().withFlowDirectiveEnum(FlowDirectiveEnum.CONTINUE));
                    switchItems.add(new SwitchItem("default", switchCase));
                }
            }
        }

        return new TaskItem(name, new Task().withSwitchTask(new SwitchTask().withSwitch(switchItems)));
    }

    @SuppressWarnings("unchecked")
    private TaskItem compileTemplateMode(Map<String, Object> config, CompilationContext ctx) {
        var resolver = ctx.compiler().schemaResolver();
        String namespace = String.valueOf(config.get("namespace"));
        if (!config.containsKey("input") || config.get("input") == null) {
            ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                    "Template-mode match requires 'input:' expression");
            return null;
        }
        String inputExpr = String.valueOf(config.get("input"));

        List<TemplateSchema> schemas = resolver.resolve(namespace);
        if (schemas.isEmpty()) {
            ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                    "Template namespace '" + namespace + "' not found in registry");
            return null;
        }

        Map<String, TemplateSchema> schemaMap = schemas.stream()
                .collect(Collectors.toMap(TemplateSchema::templateName, s -> s, (a, b) -> {
                    ctx.addDiagnostic(CompilationDiagnostic.Severity.WARNING,
                            "Duplicate template name '" + a.templateName() + "' in namespace '" + namespace + "'");
                    return a;
                }));

        var onHandlers = (Map<String, Object>) config.get("on");
        if (onHandlers == null || onHandlers.isEmpty()) {
            ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                    "Template-mode match requires 'on:' with at least one handler");
            return null;
        }

        for (String handlerName : onHandlers.keySet()) {
            if (!schemaMap.containsKey(handlerName)) {
                ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                        "Handler '" + handlerName + "' does not match any template in namespace '"
                        + namespace + "'. Available templates: " + schemaMap.keySet());
            }
        }

        for (var entry : onHandlers.entrySet()) {
            String templateName = entry.getKey();
            TemplateSchema schema = schemaMap.get(templateName);
            if (schema != null && entry.getValue() instanceof List<?> steps) {
                validateTemplateReferences(templateName, steps, schema.variables(), onHandlers.keySet(), ctx);
            }
        }

        String taskName = ctx.generateName("template-match");
        var setConfig = new SetTaskConfiguration()
                .withAdditionalProperty("_stepType", "template-match")
                .withAdditionalProperty("namespace", namespace)
                .withAdditionalProperty("input", inputExpr);

        var handlerNames = new ArrayList<>(onHandlers.keySet());
        setConfig.withAdditionalProperty("handlers", handlerNames);

        if (config.containsKey("unmatched")) {
            setConfig.withAdditionalProperty("hasUnmatched", true);
        }

        return new TaskItem(taskName, new Task().withSetTask(
                new SetTask().withSet(new Set().withSetTaskConfiguration(setConfig))));
    }

    private void validateTemplateReferences(String templateName, List<?> steps,
            Map<String, VariableConstraint> variables, java.util.Set<String> allTemplateNames,
            CompilationContext ctx) {
        for (Object step : steps) {
            if (step instanceof Map<?, ?> stepMap) {
                collectAndValidateRefs(templateName, stepMap, variables, allTemplateNames, ctx);
            }
        }
    }

    private void collectAndValidateRefs(String templateName, Map<?, ?> map,
            Map<String, VariableConstraint> variables, java.util.Set<String> allTemplateNames,
            CompilationContext ctx) {
        for (var entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s) {
                validateStringRefs(s, templateName, variables, allTemplateNames, ctx);
            } else if (value instanceof Map<?, ?> nested) {
                collectAndValidateRefs(templateName, nested, variables, allTemplateNames, ctx);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> itemMap) {
                        collectAndValidateRefs(templateName, itemMap, variables, allTemplateNames, ctx);
                    } else if (item instanceof String s) {
                        validateStringRefs(s, templateName, variables, allTemplateNames, ctx);
                    }
                }
            }
        }
    }

    private void validateStringRefs(String s, String templateName,
            Map<String, VariableConstraint> variables, java.util.Set<String> allTemplateNames,
            CompilationContext ctx) {
        Matcher m = TEMPLATE_VAR_PATTERN.matcher(s);
        while (m.find()) {
            String prefix = m.group(1);
            String varName = m.group(2);

            if (prefix.equals(templateName)) {
                if (!variables.containsKey(varName)) {
                    ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                            "Variable '${" + prefix + "." + varName + "}' not declared in template '"
                            + templateName + "'. Available: " + variables.keySet());
                } else {
                    VariableConstraint constraint = variables.get(varName);
                    if (!constraint.required()) {
                        ctx.addDiagnostic(CompilationDiagnostic.Severity.WARNING,
                                "Optional variable '${" + prefix + "." + varName
                                + "}' used without null check — may be null at runtime");
                    }
                }
            } else if (allTemplateNames.contains(prefix) && !prefix.equals(templateName)) {
                ctx.addDiagnostic(CompilationDiagnostic.Severity.ERROR,
                        "Cross-handler reference '${" + prefix + "." + varName
                        + "}' — '" + prefix + "' variables are only available in the '"
                        + prefix + "' handler");
            }
        }
    }
}
