package io.casehub.platform.compiler;

public record CompilationDiagnostic(Severity severity, String message, String yamlPath, int stepIndex) {

    public enum Severity { ERROR, WARNING }
}
