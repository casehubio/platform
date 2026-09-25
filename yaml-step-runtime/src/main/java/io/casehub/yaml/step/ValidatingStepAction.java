package io.casehub.yaml.step;

import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.core.step.StepValidator;
import io.casehub.yaml.plugin.api.ServiceRegistry;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.plugin.api.StepResult;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ValidatingStepAction implements StepAction {

    private final StepDefinition definition;
    private final StepAction delegate;
    private final Consumer<StepExecutionEvent> eventSink;

    public ValidatingStepAction(StepDefinition definition,
                                StepAction delegate,
                                Consumer<StepExecutionEvent> eventSink) {
        this.definition = definition;
        this.delegate = delegate;
        this.eventSink = eventSink;
    }

    @Override
    public StepResult execute(Map<String, Object> params, ServiceRegistry services) {
        List<String> inputErrors = StepValidator.validateStep(
                definition.name(), params, definition);
        if (!inputErrors.isEmpty()) {
            return StepResult.failed("Input validation: " + String.join("; ", inputErrors));
        }

        long start = System.nanoTime();
        StepResult result = delegate.execute(params, services);
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        if (result.isSuccess()) {
            List<String> outputErrors = StepValidator.validateOutputs(
                    result.output(), definition);
            if (!outputErrors.isEmpty()) {
                fireEvent(durationMs, false, Map.of("error",
                        "Output validation: " + String.join("; ", outputErrors)));
                return StepResult.failed("Output validation: " + String.join("; ", outputErrors));
            }
        }

        Map<String, Object> metadata = result.isSuccess()
                ? result.executionMetadata()
                : Map.of("error", ((StepResult.Failure) result).message());
        fireEvent(durationMs, result.isSuccess(), metadata);

        return result;
    }

    private void fireEvent(long durationMs, boolean success, Map<String, Object> metadata) {
        eventSink.accept(new StepExecutionEvent(
                definition.name(), durationMs, success, metadata));
    }
}
