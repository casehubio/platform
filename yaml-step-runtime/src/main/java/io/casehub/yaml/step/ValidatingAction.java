package io.casehub.yaml.step;

import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.Validator;
import io.casehub.yaml.plugin.api.ServiceRegistry;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ValidatingAction implements Action {

    private final Declaration                    declaration;
    private final Action                         delegate;
    private final Consumer<ActionExecutionEvent> eventSink;

    public ValidatingAction(Declaration declaration,
                            Action delegate,
                            Consumer<ActionExecutionEvent> eventSink) {
        this.declaration = declaration;
        this.delegate = delegate;
        this.eventSink = eventSink;
    }

    @Override
    public Result execute(Map<String, Object> params, ServiceRegistry services) {
        List<String> inputErrors = Validator.validateStep(
                declaration.name(), params, declaration);
        if (!inputErrors.isEmpty()) {
            return Result.failed("Input validation: " + String.join("; ", inputErrors));
        }

        long   start      = System.nanoTime();
        Result result     = delegate.execute(params, services);
        long   durationMs = (System.nanoTime() - start) / 1_000_000;

        if (result.isSuccess()) {
            List<String> outputErrors = Validator.validateOutputs(
                    result.output(), declaration);
            if (!outputErrors.isEmpty()) {
                fireEvent(durationMs, false, Map.of("error",
                        "Output validation: " + String.join("; ", outputErrors)));
                return Result.failed("Output validation: " + String.join("; ", outputErrors));
            }
        }

        Map<String, Object> metadata = result.isSuccess()
                ? result.executionMetadata()
                : Map.of("error", ((Result.Failure) result).message());
        fireEvent(durationMs, result.isSuccess(), metadata);

        return result;
    }

    private void fireEvent(long durationMs, boolean success, Map<String, Object> metadata) {
        String bindingType = extractBindingType(declaration.invoke());
        String classification = success ? "SUCCESS" : "FAILURE";
        eventSink.accept(new ActionExecutionEvent(
                declaration.name(), durationMs, success, metadata,
                bindingType, classification,
                null, null, null, null, null));
    }

    private static String extractBindingType(InvokeBinding binding) {
        if (binding == null) return null;
        return switch (binding) {
            case InvokeBinding.Process p -> "process";
            case InvokeBinding.Script s -> "script";
            case InvokeBinding.Agent a -> "agent";
            case InvokeBinding.Mcp m -> "mcp";
            case InvokeBinding.Rest r -> "rest";
            case InvokeBinding.Graphql g -> "graphql";
        };
    }
}
