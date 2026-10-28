package io.casehub.platform.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AgentDescriptorTest {

    @Test
    void agentIdRequired() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AgentDescriptor(null, "Name", "Desc", Map.of()));
    }

    @Test
    void metadataDefaultsToEmpty() {
        var desc = new AgentDescriptor("id", "Name", "Desc", null);
        assertThat(desc.metadata()).isNotNull().isEmpty();
    }

    @Test
    void nullableDisplayNameAndDescription() {
        var desc = new AgentDescriptor("id", null, null, Map.of());
        assertThat(desc.displayName()).isNull();
        assertThat(desc.description()).isNull();
    }
}
