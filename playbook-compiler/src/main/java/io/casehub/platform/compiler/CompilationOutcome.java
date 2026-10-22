package io.casehub.platform.compiler;

import java.util.List;

public sealed interface CompilationOutcome {

    record Success(CompilationResult result) implements CompilationOutcome {}

    record Failure(List<CompilationDiagnostic> diagnostics) implements CompilationOutcome {}
}
