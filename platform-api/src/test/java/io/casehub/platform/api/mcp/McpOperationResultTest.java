package io.casehub.platform.api.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpOperationResultTest {

    @Test
    void from_mapsExceptionFields() {
        var ex = McpCapabilityException.unsupported(
                "SearchOperations", "s3",
                List.of("Upload", "Download"), "S3 does not support search");

        var result = McpOperationResult.from(ex, "connectors", "search");

        assertThat(result.outcome()).isEqualTo(OperationOutcome.UNSUPPORTED);
        assertThat(result.domain()).isEqualTo("connectors");
        assertThat(result.operation()).isEqualTo("search");
        assertThat(result.capability()).isEqualTo("SearchOperations");
        assertThat(result.provider()).isEqualTo("s3");
        assertThat(result.supportedCapabilities()).containsExactly("Upload", "Download");
        assertThat(result.message()).isEqualTo("S3 does not support search");
    }

    @Test
    void from_unavailable_hasEmptyCapabilities() {
        var ex = McpCapabilityException.unavailable(
                "PaymentInitiation", "truelayer", "Auth expired");

        var result = McpOperationResult.from(ex, "connectors", "initiatePayment");

        assertThat(result.outcome()).isEqualTo(OperationOutcome.UNAVAILABLE);
        assertThat(result.supportedCapabilities()).isEmpty();
        assertThat(result.domain()).isEqualTo("connectors");
        assertThat(result.operation()).isEqualTo("initiatePayment");
    }

    @Test
    void recordAccessors() {
        var result = new McpOperationResult(
                OperationOutcome.ERROR, "test", "op",
                "cap", "prov", List.of("A"), "error msg");

        assertThat(result.outcome()).isEqualTo(OperationOutcome.ERROR);
        assertThat(result.message()).isEqualTo("error msg");
    }
}
