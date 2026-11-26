package io.casehub.platform.agent;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AgentResponseParseExceptionTest {

    @Test
    void carriesMessageCauseAndRawResponse() {
        var cause = new RuntimeException("bad json");
        var ex = new AgentResponseParseException("Failed to parse as Task", cause, "{malformed}");

        assertThat(ex.getMessage()).isEqualTo("Failed to parse as Task");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.rawResponse()).isEqualTo("{malformed}");
    }

    @Test
    void isUnchecked() {
        assertThat(new AgentResponseParseException("msg", null, "raw"))
                .isInstanceOf(RuntimeException.class);
    }
}
