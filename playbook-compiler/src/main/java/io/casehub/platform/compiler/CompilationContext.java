package io.casehub.platform.compiler;

import io.serverlessworkflow.api.types.TaskItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CompilationContext {

    private final String currentPath;
    private final int stepIndex;
    private final SourceMap.Builder sourceMap;
    private final CoordinationSpec.Builder coordination;
    private final List<CompilationDiagnostic> diagnostics;
    private final PlaybookCompiler compiler;

    CompilationContext(String currentPath, int stepIndex,
            SourceMap.Builder sourceMap,
            CoordinationSpec.Builder coordination,
            List<CompilationDiagnostic> diagnostics,
            PlaybookCompiler compiler) {
        this.currentPath = currentPath;
        this.stepIndex = stepIndex;
        this.sourceMap = sourceMap;
        this.coordination = coordination;
        this.diagnostics = diagnostics;
        this.compiler = compiler;
    }

    @SuppressWarnings("unchecked")
    List<TaskItem> compileSteps(List<?> steps) {
        var items = new ArrayList<TaskItem>();
        for (int i = 0; i < steps.size(); i++) {
            var step = (Map<String, Object>) steps.get(i);
            var child = child(currentPath + "/" + i, i);
            TaskItem item = compiler.compileStep(step, child);
            if (item != null) items.add(item);
        }
        return items;
    }

    CompilationContext child(String path, int index) {
        return new CompilationContext(path, index, sourceMap, coordination, diagnostics, compiler);
    }

    String generateName(String prefix) {
        String name = prefix + "_" + stepIndex;
        sourceMap.record(name, currentPath, stepIndex);
        return name;
    }

    String generateChildName(String prefix, String qualifier) {
        String name = prefix + "_" + stepIndex + "_" + qualifier;
        sourceMap.record(name, currentPath, stepIndex);
        return name;
    }

    void addPrimitive(CoordinationSpec.PrimitiveSpec spec) {
        coordination.add(spec);
    }

    void addDiagnostic(CompilationDiagnostic.Severity severity, String message) {
        diagnostics.add(new CompilationDiagnostic(severity, message, currentPath, stepIndex));
    }

    void recordSource(String taskName) {
        sourceMap.record(taskName, currentPath, stepIndex);
    }

    String currentPath() { return currentPath; }

    int stepIndex() { return stepIndex; }
}
