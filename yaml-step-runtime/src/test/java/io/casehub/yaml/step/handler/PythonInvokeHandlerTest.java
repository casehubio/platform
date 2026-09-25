package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.core.step.InvokeBinding;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PythonInvokeHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void supportsOnlyPythonBindings() {
        var handler = new PythonInvokeHandler(mapper);
        assertThat(handler.supports(new InvokeBinding.Python("script.py"))).isTrue();
        assertThat(handler.supports(new InvokeBinding.Mcp("test"))).isFalse();
    }

    @Test
    void createsNonNullAction() {
        var handler = new PythonInvokeHandler(mapper);
        var binding = new InvokeBinding.Python("steps/sentiment.py");
        var def = new io.casehub.yaml.core.step.StepDefinition("test", null,
                java.util.Map.of(), java.util.Map.of(), binding);
        assertThat(handler.create(def, binding)).isNotNull();
    }
}
