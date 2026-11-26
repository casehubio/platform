package io.casehub.platform.agent.gemini;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiCachingTest {

    private static final com.google.genai.Client STUB_CLIENT =
            com.google.genai.Client.builder().apiKey("test-key").build();

    @Test
    void cachedContent_passedWhenSet() {
        var chatModel = new GeminiNativeChatModel(STUB_CLIENT, "gemini-2.5-flash",
                                                  null, "cachedContents/abc123");

        assertThat(chatModel.cachedContent()).isEqualTo("cachedContents/abc123");
    }

    @Test
    void cachedContent_nullWhenNotSet() {
        var chatModel = new GeminiNativeChatModel(STUB_CLIENT, "gemini-2.5-flash", null);

        assertThat(chatModel.cachedContent()).isNull();
    }
}
