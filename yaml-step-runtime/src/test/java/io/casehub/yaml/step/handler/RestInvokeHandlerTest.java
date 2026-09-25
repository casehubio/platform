package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.core.step.InvokeBinding;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestInvokeHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void supportsOnlyRestBindings() {
        var handler = new RestInvokeHandler(mapper);
        assertThat(handler.supports(new InvokeBinding.Rest(null, "/api", null, null))).isTrue();
        assertThat(handler.supports(new InvokeBinding.Mcp("test"))).isFalse();
    }

    @Test
    void createsNonNullAction() {
        var handler = new RestInvokeHandler(mapper);
        var binding = new InvokeBinding.Rest("GET", "http://localhost/api", null, null);
        var def = new io.casehub.yaml.core.step.StepDefinition("test", null,
                java.util.Map.of(), java.util.Map.of(), binding);
        assertThat(handler.create(def, binding)).isNotNull();
    }
}
