package io.casehub.platform.compiler;

import io.serverlessworkflow.api.types.TaskItem;

import java.util.Map;

@FunctionalInterface
public interface StepCompiler {
    TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep, CompilationContext ctx);
}
