package io.casehub.plugin.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginCoordinatesTest {

    @Test
    void toString_formatsCorrectly() {
        var coords = new PluginCoordinates("monitoring-endpoint", "1.2.0");
        assertThat(coords.toString()).isEqualTo("monitoring-endpoint:1.2.0");
    }

    @Test
    void parse_roundTrips() {
        var coords = PluginCoordinates.parse("monitoring-endpoint:1.2.0");
        assertThat(coords.name()).isEqualTo("monitoring-endpoint");
        assertThat(coords.version()).isEqualTo("1.2.0");
    }

    @Test
    void parse_rejectsInvalidFormat() {
        assertThatThrownBy(() -> PluginCoordinates.parse("no-version"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsBlankName() {
        assertThatThrownBy(() -> new PluginCoordinates("", "1.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsNullVersion() {
        assertThatThrownBy(() -> new PluginCoordinates("foo", null))
                .isInstanceOf(NullPointerException.class);
    }
}
