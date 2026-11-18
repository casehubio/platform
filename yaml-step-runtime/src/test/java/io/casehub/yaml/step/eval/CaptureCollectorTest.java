package io.casehub.yaml.step.eval;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptureCollectorTest {

    @Test
    void captureKeepsNullValuedFields() {
        var collector = new CaptureCollector();
        Map<String, Object> output = new HashMap<>();
        output.put("name", "Trattoria");
        output.put("phone", null);

        collector.capture("restaurant", output);

        assertThat(collector.get("restaurant")).containsEntry("name", "Trattoria");
        assertThat(collector.get("restaurant")).containsKey("phone");
        assertThat(collector.get("restaurant").get("phone")).isNull();
        assertThat(collector.all().get("restaurant")).containsKey("phone");
    }

    @Test
    void captureIsADefensiveUnmodifiableCopy() {
        var collector = new CaptureCollector();
        Map<String, Object> output = new HashMap<>();
        output.put("name", "Trattoria");

        collector.capture("restaurant", output);
        output.put("name", "changed");

        assertThat(collector.get("restaurant")).containsEntry("name", "Trattoria");
        assertThatThrownBy(() -> collector.get("restaurant").put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
