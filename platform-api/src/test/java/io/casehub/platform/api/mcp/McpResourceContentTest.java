package io.casehub.platform.api.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class McpResourceContentTest {

    @Test
    void twoArgFactoryCreatesNullMimeType() {
        var c = McpResourceContent.of("uri", "hello");
        assertThat(c.uri()).isEqualTo("uri");
        assertThat(c.text()).isEqualTo("hello");
        assertThat(c.mimeType()).isNull();
    }

    @Test
    void threeArgFactorySetsMimeType() {
        var c = McpResourceContent.of("uri", "hello", "text/plain");
        assertThat(c.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void nullUriThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> McpResourceContent.of(null, "text"));
    }

    @Test
    void nullTextThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> McpResourceContent.of("uri", null));
    }
}
