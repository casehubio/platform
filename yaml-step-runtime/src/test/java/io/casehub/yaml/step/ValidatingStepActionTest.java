package io.casehub.yaml.step;

import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.core.step.StepParameter;
import io.casehub.yaml.core.step.StepParameterType;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.plugin.api.StepResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatingStepActionTest {

    private StepDefinition defWithRequiredStringInput() {
        return new StepDefinition("test", null,
                Map.of("name", new StepParameter(StepParameterType.STRING, true, null, null, null, null)),
                Map.of("result", new StepParameter(StepParameterType.STRING, false, null, null, null, null)),
                new InvokeBinding.Mcp("test"));
    }

    @Test
    void validInputDelegatesAndReturnsResult() {
        StepAction delegate = (params, services) -> StepResult.of(Map.of("result", "ok"));
        List<StepExecutionEvent> events = new ArrayList<>();

        var action = new ValidatingStepAction(defWithRequiredStringInput(), delegate, events::add);
        StepResult result = action.execute(Map.of("name", "hello"), null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("result", "ok");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).success()).isTrue();
        assertThat(events.get(0).actionName()).isEqualTo("test");
        assertThat(events.get(0).durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void invalidInputReturnsFailureWithoutDelegating() {
        boolean[] delegateCalled = {false};
        StepAction delegate = (params, services) -> {
            delegateCalled[0] = true;
            return StepResult.of(Map.of());
        };
        List<StepExecutionEvent> events = new ArrayList<>();

        var action = new ValidatingStepAction(defWithRequiredStringInput(), delegate, events::add);
        StepResult result = action.execute(Map.of(), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(delegateCalled[0]).isFalse();
        assertThat(events).isEmpty();
    }

    @Test
    void invalidOutputReturnsFailure() {
        var def = new StepDefinition("test", null,
                Map.of(),
                Map.of("count", new StepParameter(StepParameterType.INTEGER, true, null, null, null, null)),
                new InvokeBinding.Mcp("test"));

        StepAction delegate = (params, services) -> StepResult.of(Map.of("count", "not-an-integer"));
        List<StepExecutionEvent> events = new ArrayList<>();

        var action = new ValidatingStepAction(def, delegate, events::add);
        StepResult result = action.execute(Map.of(), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(((StepResult.Failure) result).message()).contains("Output validation");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).success()).isFalse();
    }

    @Test
    void executionMetadataFlowsThrough() {
        StepAction delegate = (params, services) ->
                StepResult.of(Map.of("result", "ok"), Map.of("cost", 0.05, "model", "claude"));
        List<StepExecutionEvent> events = new ArrayList<>();

        var action = new ValidatingStepAction(defWithRequiredStringInput(), delegate, events::add);
        StepResult result = action.execute(Map.of("name", "hello"), null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.executionMetadata()).containsEntry("cost", 0.05);
        assertThat(events.get(0).metadata()).containsEntry("cost", 0.05);
    }

    @Test
    void delegateFailureIsPassedThrough() {
        StepAction delegate = (params, services) -> StepResult.failed("backend error");
        List<StepExecutionEvent> events = new ArrayList<>();

        var action = new ValidatingStepAction(defWithRequiredStringInput(), delegate, events::add);
        StepResult result = action.execute(Map.of("name", "hello"), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(((StepResult.Failure) result).message()).isEqualTo("backend error");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).success()).isFalse();
        assertThat(events.get(0).metadata()).containsKey("error");
    }
}
