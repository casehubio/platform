package io.casehub.platform.compiler;

import org.openworkflow.sdk.api.types.Workflow;

public record CompilationResult(Workflow workflow, CoordinationSpec coordinationSpec, SourceMap sourceMap) {}
