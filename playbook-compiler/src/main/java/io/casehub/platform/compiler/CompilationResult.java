package io.casehub.platform.compiler;

import io.casehub.platform.agent.config.PoolDeclaration;
import io.serverlessworkflow.api.types.Workflow;

import java.util.List;

public record CompilationResult(
        Workflow workflow,
        CoordinationSpec coordinationSpec,
        SourceMap sourceMap,
        List<PoolDeclaration> poolDeclarations
) {
    public CompilationResult(Workflow workflow, CoordinationSpec coordinationSpec, SourceMap sourceMap) {
        this(workflow, coordinationSpec, sourceMap, List.of());
    }

    public CompilationResult {
        poolDeclarations = poolDeclarations != null ? List.copyOf(poolDeclarations) : List.of();
    }
}
