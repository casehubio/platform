package io.casehub.platform.agent.guardrail;

import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.ResponseFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaOutputGuardrailTest {

    private final JsonSchemaOutputGuardrail guardrail = new JsonSchemaOutputGuardrail();

    @Test
    void passesWhenNoResponseFormat() {
        var config = AgentSessionConfig.of("sys", "user");
        var result = guardrail.validate("{}", config);
        assertThat(result).isInstanceOf(GuardrailResult.Pass.class);
    }

    @Test
    void passesWhenTextFormat() {
        var config = AgentSessionConfig.of("sys", "user");
        var result = guardrail.validate("plain text", config);
        assertThat(result).isInstanceOf(GuardrailResult.Pass.class);
    }

    @Test
    void passesWhenValidJson() {
        var config = AgentSessionConfig.of("sys", "user")
            .withResponseFormat(ResponseFormat.json("test", "{\"type\":\"object\"}"));
        var result = guardrail.validate("{\"name\":\"Alice\"}", config);
        assertThat(result).isInstanceOf(GuardrailResult.Pass.class);
    }

    @Test
    void failsWhenInvalidJson() {
        var config = AgentSessionConfig.of("sys", "user")
            .withResponseFormat(ResponseFormat.json("test", "{\"type\":\"object\"}"));
        var result = guardrail.validate("not json at all", config);
        assertThat(result).isInstanceOf(GuardrailResult.Fail.class);
        assertThat(((GuardrailResult.Fail) result).reason()).contains("not valid JSON");
    }
}
