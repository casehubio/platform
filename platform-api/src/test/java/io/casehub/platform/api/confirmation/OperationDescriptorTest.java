package io.casehub.platform.api.confirmation;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OperationDescriptorTest {

    @Test
    void constructor_rejectNullSummary() {
        assertThatNullPointerException()
            .isThrownBy(() -> new OperationDescriptor(null, Map.of()));
    }

    @Test
    void constructor_nullMetadataBecomesEmptyMap() {
        var desc = new OperationDescriptor("test", null);
        assertThat(desc.metadata()).isEmpty();
    }

    @Test
    void metadata_isDefensivelyCopied() {
        var mutable = new HashMap<String, String>();
        mutable.put("key", "value");
        var desc = new OperationDescriptor("test", mutable);
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> desc.metadata().put("new", "val"));
    }

    @Test
    void summary_isAccessible() {
        var desc = new OperationDescriptor("Payment of £50", Map.of("amount", "50"));
        assertThat(desc.summary()).isEqualTo("Payment of £50");
        assertThat(desc.metadata()).containsEntry("amount", "50");
    }
}
