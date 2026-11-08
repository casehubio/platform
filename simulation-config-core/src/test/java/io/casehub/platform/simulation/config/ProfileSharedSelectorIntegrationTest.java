package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.NoOpSimulationCorpus;
import io.casehub.platform.simulation.SimulationRuntime;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileSharedSelectorIntegrationTest {

    @Test
    void pushProfileCreatesSharedSelectorAcrossMethods() {
        var config = loadConfig("""
                profiles:
                  outage-scenario:
                    corpus-selection:
                      type: state-machine
                      initial: normal
                      transitions:
                        normal:
                          outage-starts: degraded
                    methods:
                      payment.charge:
                        strategy: sequential
                        phases:
                          normal:
                            - output: APPROVED
                          degraded:
                            - output: UNAVAILABLE
                      payment.refund:
                        strategy: sequential
                        phases:
                          normal:
                            - output: REFUNDED
                          degraded:
                            - output: UNAVAILABLE
                """);

        var runtime = new SimulationRuntime(config, new NoOpSimulationCorpus<>());
        runtime.setProfileSource(config);
        runtime.pushProfile("outage-scenario");

        var charge = runtime.<Object, Object>strategyFor("payment.charge");
        var refund = runtime.<Object, Object>strategyFor("payment.refund");
        assertThat(charge).isPresent();
        assertThat(refund).isPresent();

        assertThat(charge.get().resolve("x")).isEqualTo("APPROVED");
        assertThat(refund.get().resolve("x")).isEqualTo("REFUNDED");

        runtime.advanceStateMachines("outage-starts");

        assertThat(charge.get().resolve("x")).isEqualTo("UNAVAILABLE");
        assertThat(refund.get().resolve("x")).isEqualTo("UNAVAILABLE");
    }

    @Test
    void groupKeyedSelectorIsUsedByStrategies() {
        var config = loadConfig("""
                profiles:
                  outage-scenario:
                    corpus-selection:
                      type: state-machine
                      initial: normal
                      transitions:
                        normal:
                          outage-starts: degraded
                    methods:
                      payment.charge:
                        strategy: sequential
                        phases:
                          normal:
                            - output: APPROVED
                          degraded:
                            - output: UNAVAILABLE
                      payment.refund:
                        strategy: sequential
                        phases:
                          normal:
                            - output: REFUNDED
                          degraded:
                            - output: UNAVAILABLE
                """);

        var runtime = new SimulationRuntime(config, new NoOpSimulationCorpus<>());
        runtime.setProfileSource(config);
        runtime.pushProfile("outage-scenario");

        var charge = runtime.<Object, Object>strategyFor("payment.charge");
        var refund = runtime.<Object, Object>strategyFor("payment.refund");

        var selectorInfo = runtime.allSelectorInfo();
        assertThat(selectorInfo).containsKey("__profile:outage-scenario");
        assertThat(selectorInfo.get("__profile:outage-scenario").activePhase()).isEqualTo("normal");

        runtime.advanceStateMachine("__profile:outage-scenario", "outage-starts");

        assertThat(charge.get().resolve("x")).isEqualTo("UNAVAILABLE");
        assertThat(refund.get().resolve("x")).isEqualTo("UNAVAILABLE");
        assertThat(selectorInfo.get("__profile:outage-scenario")).isNotNull();
        assertThat(runtime.allSelectorInfo().get("__profile:outage-scenario").activePhase())
                .isEqualTo("degraded");
    }

    @Test
    void keyLookupStrategyWithSharedSelector() {
        var config = loadConfig("""
                profiles:
                  outage-scenario:
                    corpus-selection:
                      type: state-machine
                      initial: normal
                      transitions:
                        normal:
                          outage-starts: degraded
                    methods:
                      payment.charge:
                        strategy: key-lookup
                        phases:
                          normal:
                            - key: charge
                              output: APPROVED
                          degraded:
                            - key: charge
                              output: UNAVAILABLE
                      payment.refund:
                        strategy: key-lookup
                        phases:
                          normal:
                            - key: refund
                              output: REFUNDED
                          degraded:
                            - key: refund
                              output: UNAVAILABLE
                """);

        var runtime = new SimulationRuntime(config, new NoOpSimulationCorpus<>());
        runtime.setProfileSource(config);
        runtime.registerExtractor("payment.charge", (Object input) -> "charge");
        runtime.registerExtractor("payment.refund", (Object input) -> "refund");
        runtime.pushProfile("outage-scenario");

        var charge = runtime.<Object, Object>strategyFor("payment.charge");
        var refund = runtime.<Object, Object>strategyFor("payment.refund");
        assertThat(charge).isPresent();
        assertThat(refund).isPresent();

        assertThat(charge.get().resolve("anything")).isEqualTo("APPROVED");
        assertThat(refund.get().resolve("anything")).isEqualTo("REFUNDED");

        runtime.advanceStateMachines("outage-starts");

        assertThat(charge.get().resolve("anything")).isEqualTo("UNAVAILABLE");
        assertThat(refund.get().resolve("anything")).isEqualTo("UNAVAILABLE");
    }

    private static YamlSimulationConfig loadConfig(String yaml) {
        return new YamlSimulationConfig(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
