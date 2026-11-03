package io.casehub.platform.compiler;

import io.casehub.platform.agent.config.PoolDeclaration;
import io.serverlessworkflow.api.types.Workflow;

import java.util.List;

public record CompilationResult(
        Workflow workflow,
        CoordinationSpec coordinationSpec,
        SourceMap sourceMap,
        List<PoolDeclaration> poolDeclarations,
        PlaybookVariableContext variableContext
) {
    public CompilationResult(Workflow workflow, CoordinationSpec coordinationSpec, SourceMap sourceMap) {
        this(workflow, coordinationSpec, sourceMap, List.of(), PlaybookVariableContext.EMPTY);
    }

    public CompilationResult(Workflow workflow, CoordinationSpec coordinationSpec,
                             SourceMap sourceMap, List<PoolDeclaration> poolDeclarations) {
        this(workflow, coordinationSpec, sourceMap, poolDeclarations, PlaybookVariableContext.EMPTY);
    }

    public CompilationResult {
        poolDeclarations = poolDeclarations != null ? List.copyOf(poolDeclarations) : List.of();
        variableContext  = variableContext != null ? variableContext : PlaybookVariableContext.EMPTY;
    }
}
