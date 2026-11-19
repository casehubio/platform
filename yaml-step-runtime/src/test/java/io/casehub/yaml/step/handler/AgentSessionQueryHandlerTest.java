package io.casehub.yaml.step.handler;

import io.casehub.platform.agent.ResponseFormat;
import io.casehub.platform.agent.ResponseFormatType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSessionQueryHandlerTest {

    @Test
    void toResponseFormat_null_returnsNull() {
        assertThat(AgentSessionQueryHandler.toResponseFormat(null)).isNull();
    }

    @Test
    void toResponseFormat_passesThrough_responseFormat() {
        var format = ResponseFormat.json("weather", "{\"type\":\"object\"}");
        assertThat(AgentSessionQueryHandler.toResponseFormat(format)).isSameAs(format);
    }

    @Test
    void toResponseFormat_map_withNameAndSchema() {
        var raw = Map.of("type", "json", "name", "result", "schema", "{\"type\":\"object\"}");
        var format = AgentSessionQueryHandler.toResponseFormat(raw);
        assertThat(format).isNotNull();
        assertThat(format.type()).isEqualTo(ResponseFormatType.JSON);
        assertThat(format.name()).isEqualTo("result");
        assertThat(format.schema()).isEqualTo("{\"type\":\"object\"}");
    }

    @Test
    void toResponseFormat_map_jsonAny() {
        var raw = Map.of("type", "json");
        var format = AgentSessionQueryHandler.toResponseFormat(raw);
        assertThat(format).isNotNull();
        assertThat(format.type()).isEqualTo(ResponseFormatType.JSON);
        assertThat(format.name()).isNull();
    }

    @Test
    void toResponseFormat_map_text() {
        var raw = Map.of("type", "text");
        var format = AgentSessionQueryHandler.toResponseFormat(raw);
        assertThat(format).isEqualTo(ResponseFormat.TEXT);
    }

    @Test
    void toResponseFormat_map_defaultsToJson() {
        var raw = Map.of("name", "output", "schema", "{\"type\":\"string\"}");
        var format = AgentSessionQueryHandler.toResponseFormat(raw);
        assertThat(format).isNotNull();
        assertThat(format.type()).isEqualTo(ResponseFormatType.JSON);
        assertThat(format.name()).isEqualTo("output");
    }

    @Test
    void toResponseFormat_unknownType_returnsNull() {
        assertThat(AgentSessionQueryHandler.toResponseFormat("not-a-map")).isNull();
    }
}
