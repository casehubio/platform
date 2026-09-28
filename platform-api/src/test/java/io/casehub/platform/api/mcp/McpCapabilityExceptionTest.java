package io.casehub.platform.api.mcp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpCapabilityExceptionTest {

    @Test
    void unsupported_setsOutcomeAndMetadata() {
        var ex = McpCapabilityException.unsupported(
                "SearchOperations", "s3",
                List.of("Upload", "Download", "Delete"),
                "S3 does not support search");

        assertThat(ex.outcome()).isEqualTo(OperationOutcome.UNSUPPORTED);
        assertThat(ex.capability()).isEqualTo("SearchOperations");
        assertThat(ex.provider()).isEqualTo("s3");
        assertThat(ex.supportedCapabilities()).containsExactly("Upload", "Download", "Delete");
        assertThat(ex.getMessage()).isEqualTo("S3 does not support search");
    }

    @Test
    void unavailable_setsOutcomeAndEmptyCapabilities() {
        var ex = McpCapabilityException.unavailable(
                "PaymentInitiation", "truelayer",
                "Provider auth expired");

        assertThat(ex.outcome()).isEqualTo(OperationOutcome.UNAVAILABLE);
        assertThat(ex.capability()).isEqualTo("PaymentInitiation");
        assertThat(ex.provider()).isEqualTo("truelayer");
        assertThat(ex.supportedCapabilities()).isEmpty();
        assertThat(ex.getMessage()).isEqualTo("Provider auth expired");
    }

    @Test
    void supportedCapabilities_isDefensivelyCopied() {
        var mutable = new ArrayList<>(List.of("A", "B"));
        var ex = McpCapabilityException.unsupported("cap", "prov", mutable, "msg");
        mutable.add("C");
        assertThat(ex.supportedCapabilities()).containsExactly("A", "B");
    }

    @Test
    void isRuntimeException() {
        var ex = McpCapabilityException.unsupported("cap", "prov", List.of(), "msg");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
