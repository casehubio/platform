package io.casehub.platform.simulation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorpusSelectorConfigTest {

    @Test
    void stateMachineConfigCarriesAllFields() {
        var phases = Map.<String, List<InvocationRecord<Object, Object>>>of(
                "pending", List.of(InvocationRecord.of("t1", "k1", "in", "out-pending")),
                "completed", List.of(InvocationRecord.of("t1", "k1", "in", "out-completed")));
        var transitions = Map.of("pending", Map.of("pay", "completed"));

        var config = new CorpusSelectorConfig.StateMachine("pending", transitions, phases);

        assertThat(config.initial()).isEqualTo("pending");
        assertThat(config.transitions()).containsKey("pending");
        assertThat(config.phases()).hasSize(2);
    }

    @Test
    void durationConfigCarriesOrderedPhases() {
        var orderedPhases = List.of(
                new CorpusSelectorConfig.DurationPhase("normal", Duration.ofSeconds(10)),
                new CorpusSelectorConfig.DurationPhase("degraded", null));
        var phases = Map.<String, List<InvocationRecord<Object, Object>>>of(
                "normal", List.of(), "degraded", List.of());

        var config = new CorpusSelectorConfig.DurationBased(orderedPhases, phases);

        assertThat(config.orderedPhases()).hasSize(2);
        assertThat(config.orderedPhases().get(0).duration()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.orderedPhases().get(1).duration()).isNull();
    }

    @Test
    void lookupCountConfigCarriesOrderedPhases() {
        var orderedPhases = List.of(
                new CorpusSelectorConfig.CountPhase("pending", 3),
                new CorpusSelectorConfig.CountPhase("completed", 0));
        var phases = Map.<String, List<InvocationRecord<Object, Object>>>of(
                "pending", List.of(), "completed", List.of());

        var config = new CorpusSelectorConfig.LookupCount(orderedPhases, phases);

        assertThat(config.orderedPhases()).hasSize(2);
        assertThat(config.orderedPhases().get(0).count()).isEqualTo(3);
    }

    @Test
    void weightedConfigCarriesWeightedPhases() {
        var weightedPhases = List.of(
                new CorpusSelectorConfig.WeightedPhase("normal", 0.8),
                new CorpusSelectorConfig.WeightedPhase("error", 0.2));
        var phases = Map.<String, List<InvocationRecord<Object, Object>>>of(
                "normal", List.of(), "error", List.of());

        var config = new CorpusSelectorConfig.Weighted(weightedPhases, phases);

        assertThat(config.weightedPhases()).hasSize(2);
        assertThat(config.weightedPhases().get(0).weight()).isEqualTo(0.8);
    }

    @Test
    void sealedInterfaceIsExhaustive() {
        CorpusSelectorConfig config = new CorpusSelectorConfig.StateMachine(
                "init", Map.of(), Map.of());

        var result = switch (config) {
            case CorpusSelectorConfig.StateMachine sm -> "sm";
            case CorpusSelectorConfig.DurationBased d -> "d";
            case CorpusSelectorConfig.LookupCount lc -> "lc";
            case CorpusSelectorConfig.Weighted w -> "w";
        };
        assertThat(result).isEqualTo("sm");
    }

    @Test
    void phasesMethodWorksOnAllVariants() {
        var phases = Map.<String, List<InvocationRecord<Object, Object>>>of("a", List.of());

        assertThat(new CorpusSelectorConfig.StateMachine("a", Map.of(), phases).phases()).hasSize(1);
        assertThat(new CorpusSelectorConfig.DurationBased(List.of(), phases).phases()).hasSize(1);
        assertThat(new CorpusSelectorConfig.LookupCount(List.of(), phases).phases()).hasSize(1);
        assertThat(new CorpusSelectorConfig.Weighted(List.of(), phases).phases()).hasSize(1);
    }
}
