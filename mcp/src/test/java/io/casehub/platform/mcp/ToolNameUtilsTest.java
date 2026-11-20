package io.casehub.platform.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolNameUtilsTest {

    @Test
    void noOpWhenNothingToNormalise() {
        assertThat(ToolNameUtils.normalise("connectors")).isEqualTo("connectors");
    }

    @Test
    void slashesReplacedWithUnderscores() {
        assertThat(ToolNameUtils.normalise("connectors/voice")).isEqualTo("connectors_voice");
    }

    @Test
    void multipleSlashesNormalised() {
        assertThat(ToolNameUtils.normalise("a/b/c")).isEqualTo("a_b_c");
    }

    @Test
    void toolNameCombinesDomainAndOperation() {
        assertThat(ToolNameUtils.toolName("connectors/voice", "speak"))
                .isEqualTo("connectors_voice_speak");
    }

    @Test
    void toolNameWithSimpleDomain() {
        assertThat(ToolNameUtils.toolName("connectors", "send"))
                .isEqualTo("connectors_send");
    }

    @Test
    void reportToolName() {
        assertThat(ToolNameUtils.reportToolName("connectors/voice"))
                .isEqualTo("connectors_voice_report");
    }

    @Test
    void reportToolNameSimpleDomain() {
        assertThat(ToolNameUtils.reportToolName("connectors"))
                .isEqualTo("connectors_report");
    }
}
