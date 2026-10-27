package io.casehub.platform.compiler;

import io.serverlessworkflow.api.types.Workflow;

public record CompilationResult(Workflow workflow, CoordinationSpec coordinationSpec, SourceMap sourceMap) {}
