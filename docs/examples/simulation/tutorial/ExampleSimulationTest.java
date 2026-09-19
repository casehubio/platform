package io.casehub.example.simulation;

import io.casehub.platform.simulation.Simulation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simulation tutorial — three ways to use the framework.
 *
 * 1. Fluent harness (unit test, no CDI)
 * 2. YAML config (integration test, CDI-driven)
 * 3. Overlay + verification (scenario testing)
 */
class ExampleSimulationTest {

    // --- 1. Fluent harness: 3 lines for the common case ---

    @Test
    void fluentHarness_stubReturnsExpectedValue() {
        var sim = Simulation.forTest()
                .stub("case-memory-store.query", "cardiology", "Lab results")
                .build();

        assertThat(sim.<String, String>resolve("case-memory-store.query", "cardiology"))
                .isEqualTo("Lab results");
    }

    @Test
    void fluentHarness_seedReturnsInOrder() {
        var sim = Simulation.forTest()
                .seed("agent-provider.invoke", "hello", "Hi there!")
                .seed("agent-provider.invoke", "bye", "Goodbye!")
                .build();

        assertThat(sim.<String, String>resolve("agent-provider.invoke", "any"))
                .isEqualTo("Hi there!");
        assertThat(sim.<String, String>resolve("agent-provider.invoke", "any"))
                .isEqualTo("Goodbye!");
    }

    // --- 3. Overlay + verification: scenario testing ---

    @Test
    void overlay_recordsAndVerifiesCalls() {
        var sim = Simulation.forTest()
                .stub("case-memory-store.query", "cardiology", "Lab results")
                .stub("case-memory-store.query", "neurology", "MRI results")
                .build();

        var overlay = sim.overlay();

        sim.resolve("case-memory-store.query", "cardiology");
        sim.resolve("case-memory-store.query", "neurology");

        sim.verifier()
                .method("case-memory-store.query")
                .wasCalled(2);

        sim.popOverlay(overlay);
    }
}
