package io.casehub.platform.simulation.event;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTriggerTest {

    @Test
    void constructsWithRequiredFields() {
        var trigger = new EventTrigger("io.casehub.work.workitem.completed", "tenant-1", Map.of());
        assertThat(trigger.eventType()).isEqualTo("io.casehub.work.workitem.completed");
        assertThat(trigger.tenancyId()).isEqualTo("tenant-1");
        assertThat(trigger.context()).isEmpty();
    }

    @Test
    void nullContextDefaultsToEmptyMap() {
        var trigger = new EventTrigger("type", "tenant", null);
        assertThat(trigger.context()).isNotNull().isEmpty();
    }

    @Test
    void contextIsDefensivelyCopied() {
        var mutable = new HashMap<String, Object>();
        mutable.put("key", "value");
        var trigger = new EventTrigger("type", "tenant", mutable);
        mutable.put("new", "entry");
        assertThat(trigger.context()).doesNotContainKey("new");
    }

    @Test
    void rejectsNullEventType() {
        assertThatThrownBy(() -> new EventTrigger(null, "tenant", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankTenancyId() {
        assertThatThrownBy(() -> new EventTrigger("type", "  ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emissionResultReportsCorrectly() {
        var result = new EmissionResult(List.of(), List.of());
        assertThat(result.hasFailures()).isFalse();
        assertThat(result.emittedCount()).isZero();
    }

    @Test
    void eventSourceConfigRejectsNullQualifiedName() {
        assertThatThrownBy(() -> new EventSourceConfig(null, "type", "tenant"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
