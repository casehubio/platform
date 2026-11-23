package io.casehub.platform.compiler;

import io.casehub.platform.agent.config.PoolDeclaration;
import io.serverlessworkflow.api.types.Workflow;

import java.util.List;

public record CompilationResult(
        Workflow workflow,
        CoordinationSpec coordinationSpec,
        SourceMap sourceMap,
        List<PoolDeclaration> poolDeclarations,
        PlaybookVariableContext variableContext,
        TemplateSpec templateSpec
) {
    public CompilationResult(Workflow workflow, CoordinationSpec coordinationSpec, SourceMap sourceMap) {
        this(workflow, coordinationSpec, sourceMap, List.of(), PlaybookVariableContext.EMPTY, null);
    }

    public CompilationResult(Workflow workflow, CoordinationSpec coordinationSpec,
                             SourceMap sourceMap, List<PoolDeclaration> poolDeclarations) {
        this(workflow, coordinationSpec, sourceMap, poolDeclarations, PlaybookVariableContext.EMPTY, null);
    }

    public CompilationResult(Workflow workflow, CoordinationSpec coordinationSpec,
                             SourceMap sourceMap, List<PoolDeclaration> poolDeclarations,
                             PlaybookVariableContext variableContext) {
        this(workflow, coordinationSpec, sourceMap, poolDeclarations, variableContext, null);
    }

    public CompilationResult {
        poolDeclarations = poolDeclarations != null ? List.copyOf(poolDeclarations) : List.of();
        variableContext  = variableContext != null ? variableContext : PlaybookVariableContext.EMPTY;
    }
}
