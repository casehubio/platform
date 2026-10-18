package io.casehub.platform.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseFormatTest {

    @Test
    void textConstantHasCorrectType() {
        assertThat(ResponseFormat.TEXT.type()).isEqualTo(ResponseFormatType.TEXT);
        assertThat(ResponseFormat.TEXT.name()).isNull();
        assertThat(ResponseFormat.TEXT.schema()).isNull();
    }

    @Test
    void jsonFactoryRequiresNameAndSchema() {
        var format = ResponseFormat.json("weather", "{\"type\":\"object\"}");
        assertThat(format.type()).isEqualTo(ResponseFormatType.JSON);
        assertThat(format.name()).isEqualTo("weather");
        assertThat(format.schema()).isEqualTo("{\"type\":\"object\"}");
    }

    @Test
    void jsonFactoryRejectsNullName() {
        assertThatThrownBy(() -> ResponseFormat.json(null, "{\"type\":\"object\"}"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void jsonFactoryRejectsNullSchema() {
        assertThatThrownBy(() -> ResponseFormat.json("weather", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void jsonAnyHasNullNameAndSchema() {
        var format = ResponseFormat.jsonAny();
        assertThat(format.type()).isEqualTo(ResponseFormatType.JSON);
        assertThat(format.name()).isNull();
        assertThat(format.schema()).isNull();
    }

    @Test
    void rejectsJsonWithNameButNoSchema() {
        assertThatThrownBy(() -> new ResponseFormat(ResponseFormatType.JSON, "weather", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsJsonWithSchemaButNoName() {
        assertThatThrownBy(() -> new ResponseFormat(ResponseFormatType.JSON, null, "{\"type\":\"object\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullType() {
        assertThatThrownBy(() -> new ResponseFormat(null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
