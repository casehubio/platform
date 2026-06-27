package io.casehub.platform.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AgentEventTest {

    // ── ThinkingDelta ────────────────────────────────────────────────────────

    @Test
    void thinkingDelta_carriesText() {
        var delta = new AgentEvent.ThinkingDelta("reasoning");
        assertThat(delta.text()).isEqualTo("reasoning");
    }

    @Test
    void thinkingDelta_rejectsNull() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AgentEvent.ThinkingDelta(null));
    }

    @Test
    void thinkingDelta_rejectsEmpty() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AgentEvent.ThinkingDelta(""));
    }

    // ── ToolCallDelta ────────────────────────────────────────────────────────

    @Test
    void toolCallDelta_carriesAllFields() {
        var delta = new AgentEvent.ToolCallDelta(0, "call_1", "get_weather", "{\"ci");
        assertThat(delta.index()).isZero();
        assertThat(delta.id()).isEqualTo("call_1");
        assertThat(delta.name()).isEqualTo("get_weather");
        assertThat(delta.partialArguments()).isEqualTo("{\"ci");
    }

    @Test
    void toolCallDelta_allowsNullId() {
        var delta = new AgentEvent.ToolCallDelta(0, null, "tool", "{");
        assertThat(delta.id()).isNull();
    }

    @Test
    void toolCallDelta_rejectsNegativeIndex() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AgentEvent.ToolCallDelta(-1, "id", "tool", "{"));
    }

    @Test
    void toolCallDelta_rejectsNullName() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AgentEvent.ToolCallDelta(0, "id", null, "{"));
    }

    @Test
    void toolCallDelta_rejectsBlankName() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AgentEvent.ToolCallDelta(0, "id", "  ", "{"));
    }

    @Test
    void toolCallDelta_rejectsNullPartialArguments() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AgentEvent.ToolCallDelta(0, "id", "tool", null));
    }

    @Test
    void toolCallDelta_rejectsEmptyPartialArguments() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AgentEvent.ToolCallDelta(0, "id", "tool", ""));
    }

    // ── ToolCallComplete ─────────────────────────────────────────────────────

    @Test
    void toolCallComplete_carriesAllFields() {
        var complete = new AgentEvent.ToolCallComplete(0, "call_1", "get_weather", "{\"city\":\"Munich\"}");
        assertThat(complete.index()).isZero();
        assertThat(complete.id()).isEqualTo("call_1");
        assertThat(complete.name()).isEqualTo("get_weather");
        assertThat(complete.arguments()).isEqualTo("{\"city\":\"Munich\"}");
    }

    @Test
    void toolCallComplete_allowsNullId() {
        var complete = new AgentEvent.ToolCallComplete(0, null, "tool", "{}");
        assertThat(complete.id()).isNull();
    }

    @Test
    void toolCallComplete_rejectsNegativeIndex() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AgentEvent.ToolCallComplete(-1, "id", "tool", "{}"));
    }

    @Test
    void toolCallComplete_rejectsNullName() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AgentEvent.ToolCallComplete(0, "id", null, "{}"));
    }

    @Test
    void toolCallComplete_rejectsBlankName() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AgentEvent.ToolCallComplete(0, "id", " ", "{}"));
    }

    @Test
    void toolCallComplete_rejectsNullArguments() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AgentEvent.ToolCallComplete(0, "id", "tool", null));
    }

    @Test
    void toolCallComplete_rejectsEmptyArguments() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AgentEvent.ToolCallComplete(0, "id", "tool", ""));
    }

    // ── ToolResult ───────────────────────────────────────────────────────────

    @Test
    void toolResult_carriesAllFields() {
        var result = new AgentEvent.ToolResult("call_1", "{\"temp\":22}", false);
        assertThat(result.toolCallId()).isEqualTo("call_1");
        assertThat(result.content()).isEqualTo("{\"temp\":22}");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void toolResult_allowsNullToolCallId() {
        var result = new AgentEvent.ToolResult(null, "output", false);
        assertThat(result.toolCallId()).isNull();
    }

    @Test
    void toolResult_allowsEmptyContent() {
        var result = new AgentEvent.ToolResult("id", "", false);
        assertThat(result.content()).isEmpty();
    }

    @Test
    void toolResult_rejectsNullContent() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AgentEvent.ToolResult("id", null, false));
    }

    @Test
    void toolResult_errorFlag() {
        var result = new AgentEvent.ToolResult("call_1", "timeout", true);
        assertThat(result.isError()).isTrue();
    }

    // ── TextDelta (existing — verify unchanged v1 contract) ──────────────────

    @Test
    void textDelta_allowsEmptyText() {
        var delta = new AgentEvent.TextDelta("");
        assertThat(delta.text()).isEmpty();
    }
}
