package io.casehub.platform.api.label;

import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabelDescriptorTest {

    @Test
    void displayNameDefaultsToLastSegment() {
        var descriptor = new LabelDescriptor("t1", Path.of("IT", "Hardware"), null, 0);
        assertThat(descriptor.displayName()).isEqualTo("Hardware");
    }

    @Test
    void blankDisplayNameDefaultsToLastSegment() {
        var descriptor = new LabelDescriptor("t1", Path.of("IT", "Hardware"), "  ", 0);
        assertThat(descriptor.displayName()).isEqualTo("Hardware");
    }

    @Test
    void explicitDisplayNamePreserved() {
        var descriptor = new LabelDescriptor("t1", Path.of("IT"), "Information Technology", 0);
        assertThat(descriptor.displayName()).isEqualTo("Information Technology");
    }

    @Test
    void metadataDefensiveCopy() {
        var mutable = new java.util.HashMap<String, String>();
        mutable.put("key", "value");
        var descriptor = new LabelDescriptor("t1", Path.of("IT"), "IT", 0, mutable);

        assertThatThrownBy(() -> descriptor.metadata().put("new", "entry"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullMetadataBecomesEmptyMap() {
        var descriptor = new LabelDescriptor("t1", Path.of("IT"), "IT", 0, null);
        assertThat(descriptor.metadata()).isEmpty();
    }

    @Test
    void tenancyIdRequired() {
        assertThatThrownBy(() -> new LabelDescriptor(null, Path.of("IT"), "IT", 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void pathRequired() {
        assertThatThrownBy(() -> new LabelDescriptor("t1", null, "IT", 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void convenienceConstructorSetsEmptyMetadata() {
        var descriptor = new LabelDescriptor("t1", Path.of("IT"), "IT", 0);
        assertThat(descriptor.metadata()).isEmpty();
    }
}
