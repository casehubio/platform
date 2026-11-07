package io.casehub.platform.simulation;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public sealed interface CorpusSelectorConfig {

    Map<String, List<InvocationRecord<Object, Object>>> phases();

    record StateMachine(
            String initial,
            Map<String, Map<String, String>> transitions,
            Map<String, List<InvocationRecord<Object, Object>>> phases
    ) implements CorpusSelectorConfig {}

    record DurationBased(
            List<DurationPhase> orderedPhases,
            Map<String, List<InvocationRecord<Object, Object>>> phases
    ) implements CorpusSelectorConfig {}

    record LookupCount(
            List<CountPhase> orderedPhases,
            Map<String, List<InvocationRecord<Object, Object>>> phases
    ) implements CorpusSelectorConfig {}

    record Weighted(
            List<WeightedPhase> weightedPhases,
            Map<String, List<InvocationRecord<Object, Object>>> phases
    ) implements CorpusSelectorConfig {}

    record DurationPhase(String name, Duration duration) {}

    record CountPhase(String name, int count) {}

    record WeightedPhase(String name, double weight) {}
}
