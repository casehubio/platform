package io.casehub.platform.graphql.generator.infer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InferredCallChainTest {

    @Test
    void complete_with_spi_and_target_method() {
        var chain = new InferredCallChain(
                "io.casehub.connectors.bank.spi.BankPlatform", "platform",
                "io.casehub.connectors.bank.spi.AccountInformation", "accountInformation",
                List.of("userId()"), "balance", List.of("accountId"),
                Map.of(), "balance", null);
        assertThat(chain.isComplete()).isTrue();
    }

    @Test
    void incomplete_without_spi_type() {
        var chain = new InferredCallChain(
                null, null, null, null, null,
                "balance", List.of("accountId"),
                Map.of(), null, null);
        assertThat(chain.isComplete()).isFalse();
    }

    @Test
    void incomplete_without_target_method() {
        var chain = new InferredCallChain(
                "io.casehub.connectors.bank.spi.BankPlatform", "platform",
                null, null, null, null, null,
                Map.of(), null, null);
        assertThat(chain.isComplete()).isFalse();
    }

    @Test
    void incomplete_with_capability_but_no_accessor() {
        var chain = new InferredCallChain(
                "io.casehub.connectors.bank.spi.BankPlatform", "platform",
                "io.casehub.connectors.bank.spi.AccountInformation", null,
                null, "balance", List.of("accountId"),
                Map.of(), null, null);
        assertThat(chain.isComplete()).isFalse();
    }

    @Test
    void incomplete_with_failure_reason() {
        var chain = new InferredCallChain(
                "io.casehub.connectors.bank.spi.BankPlatform", "platform",
                null, null, null, "balance", List.of("accountId"),
                Map.of(), null, InferenceFailure.VAR_TYPED_SPI);
        assertThat(chain.isComplete()).isFalse();
    }

    @Test
    void complete_without_capability_routing() {
        var chain = new InferredCallChain(
                "io.casehub.connectors.bank.spi.BankPlatform", "platform",
                null, null, null, "balance", List.of("accountId"),
                Map.of(), null, null);
        assertThat(chain.isComplete()).isTrue();
    }
}
