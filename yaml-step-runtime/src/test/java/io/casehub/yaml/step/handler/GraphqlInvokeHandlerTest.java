package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.core.step.InvokeBinding;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraphqlInvokeHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void supportsOnlyGraphqlBindings() {
        var handler = new GraphqlInvokeHandler(mapper, "http://localhost/graphql");
        assertThat(handler.supports(new InvokeBinding.Graphql("{ test }"))).isTrue();
        assertThat(handler.supports(new InvokeBinding.Mcp("test"))).isFalse();
    }

    @Test
    void createsNonNullAction() {
        var handler = new GraphqlInvokeHandler(mapper, "http://localhost/graphql");
        var binding = new InvokeBinding.Graphql("{ positions { symbol } }");
        var def = new io.casehub.yaml.core.step.StepDefinition("test", null,
                java.util.Map.of(), java.util.Map.of(), binding);
        assertThat(handler.create(def, binding)).isNotNull();
    }
}
