package io.casehub.yaml.step;

import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.plugin.api.Parameter;
import io.casehub.yaml.plugin.api.ParameterType;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatingActionTest {

    private Declaration defWithRequiredStringInput() {
        return new Declaration("test", null,
                Map.of("name", new Parameter(ParameterType.STRING, true, null, null, null, null)),
                Map.of("result", new Parameter(ParameterType.STRING, false, null, null, null, null)),
                new InvokeBinding.Mcp("test"));
    }

    @Test
    void validInputDelegatesAndReturnsResult() {
        Action                   delegate = (params, services) -> Result.of(Map.of("result", "ok"));
        List<ActionExecutionEvent> events   = new ArrayList<>();

        var    action = new ValidatingAction(defWithRequiredStringInput(), delegate, events::add);
        Result result = action.execute(Map.of("name", "hello"), null);

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
        Action delegate = (params, services) -> {
            delegateCalled[0] = true;
            return Result.of(Map.of());
        };
        List<ActionExecutionEvent> events = new ArrayList<>();

        var    action = new ValidatingAction(defWithRequiredStringInput(), delegate, events::add);
        Result result = action.execute(Map.of(), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(delegateCalled[0]).isFalse();
        assertThat(events).isEmpty();
    }

    @Test
    void invalidOutputReturnsFailure() {
        var def = new Declaration("test", null,
                Map.of(),
                Map.of("count", new Parameter(ParameterType.INTEGER, true, null, null, null, null)),
                new InvokeBinding.Mcp("test"));

        Action                   delegate = (params, services) -> Result.of(Map.of("count", "not-an-integer"));
        List<ActionExecutionEvent> events   = new ArrayList<>();

        var    action = new ValidatingAction(def, delegate, events::add);
        Result result = action.execute(Map.of(), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(((Result.Failure) result).message()).contains("Output validation");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).success()).isFalse();
    }

    @Test
    void executionMetadataFlowsThrough() {
        Action delegate = (params, services) ->
                Result.of(Map.of("result", "ok"), Map.of("cost", 0.05, "model", "claude"));
        List<ActionExecutionEvent> events = new ArrayList<>();

        var    action = new ValidatingAction(defWithRequiredStringInput(), delegate, events::add);
        Result result = action.execute(Map.of("name", "hello"), null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.executionMetadata()).containsEntry("cost", 0.05);
        assertThat(events.get(0).metadata()).containsEntry("cost", 0.05);
    }

    @Test
    void delegateFailureIsPassedThrough() {
        Action                   delegate = (params, services) -> Result.failed("backend error");
        List<ActionExecutionEvent> events   = new ArrayList<>();

        var    action = new ValidatingAction(defWithRequiredStringInput(), delegate, events::add);
        Result result = action.execute(Map.of("name", "hello"), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(((Result.Failure) result).message()).isEqualTo("backend error");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).success()).isFalse();
        assertThat(events.get(0).metadata()).containsKey("error");
    }

    @Test
    void eventIncludesBindingType() {
        List<ActionExecutionEvent> events  = new ArrayList<>();
        var                      binding = new InvokeBinding.Process("/bin/echo", List.of("hi"), "raw", null, null, null, null);
        var                      def     = new Declaration("test-step", null, Map.of(), Map.of(), binding);
        var action = new ValidatingAction(def,
                                          (params, svc) -> Result.of(Map.of(), Map.of()),
                                          events::add);

        action.execute(Map.of(), null);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).bindingType()).isEqualTo("process");
    }

    @Test
    void eventClassifiesSuccess() {
        List<ActionExecutionEvent> events = new ArrayList<>();
        var                      def    = new Declaration("test", null, Map.of(), Map.of(), new InvokeBinding.Mcp("t"));
        var action = new ValidatingAction(def,
                                          (params, svc) -> Result.of(Map.of(), Map.of()),
                                          events::add);

        action.execute(Map.of(), null);

        assertThat(events.get(0).resultClassification()).isEqualTo("SUCCESS");
    }

    @Test
    void eventClassifiesFailure() {
        List<ActionExecutionEvent> events = new ArrayList<>();
        var                      def    = new Declaration("test", null, Map.of(), Map.of(), new InvokeBinding.Mcp("t"));
        var action = new ValidatingAction(def,
                                          (params, svc) -> Result.failed("something broke"),
                                          events::add);

        action.execute(Map.of(), null);

        assertThat(events.get(0).resultClassification()).isEqualTo("FAILURE");
    }

    @Test
    void eventNullBindingTypeWhenNoBinding() {
        List<ActionExecutionEvent> events = new ArrayList<>();
        var                      def    = new Declaration("test", null, Map.of(), Map.of(), null);
        var action = new ValidatingAction(def,
                                          (params, svc) -> Result.of(Map.of(), Map.of()),
                                          events::add);

        action.execute(Map.of(), null);

        assertThat(events.get(0).bindingType()).isNull();
    }

    @Test
    void eventBindingTypeScript() {
        List<ActionExecutionEvent> events = new ArrayList<>();
        var                      def    = new Declaration("test", null, Map.of(), Map.of(),
                new InvokeBinding.Script("python3", "s.py", null, null, null));
        var action = new ValidatingAction(def,
                                          (params, svc) -> Result.of(Map.of(), Map.of()),
                                          events::add);

        action.execute(Map.of(), null);

        assertThat(events.get(0).bindingType()).isEqualTo("script");
    }

    @Test
    void eventBindingTypeAgent() {
        List<ActionExecutionEvent> events = new ArrayList<>();
        var def = new Declaration("test", null, Map.of(), Map.of(),
                                     new InvokeBinding.Agent("analyst", null, null, false));
        var action = new ValidatingAction(def,
                                          (params, svc) -> Result.of(Map.of(), Map.of()),
                                          events::add);

        action.execute(Map.of(), null);

        assertThat(events.get(0).bindingType()).isEqualTo("agent");
    }
}
