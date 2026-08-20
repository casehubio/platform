package io.casehub.platform.api.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class McpResourceRegisteredTest {

    @Test
    void carriesDescriptor() {
        var desc = McpResourceDescriptor.of("n", "u", null, "d");
        var event = new McpResourceRegistered(desc);
        assertThat(event.descriptor()).isSameAs(desc);
    }

    @Test
    void nullDescriptorThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new McpResourceRegistered(null));
    }

    @Test
    void updatedCarriesUri() {
        var event = new McpResourceUpdated("casehub://index");
        assertThat(event.uri()).isEqualTo("casehub://index");
    }

    @Test
    void updatedNullUriThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new McpResourceUpdated(null));
    }
}
