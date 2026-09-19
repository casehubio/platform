package io.casehub.platform.simulation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationTest {

    @Test
    void stubImpliesKeyLookupStrategy() {
        var sim = Simulation.forTest()
                .stub("greeting.greet", "Alice", "Hello Alice!")
                .stub("greeting.greet", "Bob", "Hello Bob!")
                .build();

        assertThat(sim.<String, String>resolve("greeting.greet", "Alice"))
                .isEqualTo("Hello Alice!");
        assertThat(sim.<String, String>resolve("greeting.greet", "Bob"))
                .isEqualTo("Hello Bob!");
        assertThat(sim.<String, String>resolve("greeting.greet", "Alice"))
                .isEqualTo("Hello Alice!");
    }

    @Test
    void seedImpliesSequentialStrategy() {
        var sim = Simulation.forTest()
                .seed("greeting.greet", "Alice", "Hello Alice!")
                .seed("greeting.greet", "Bob", "Hello Bob!")
                .build();

        assertThat(sim.<String, String>resolve("greeting.greet", "anyone"))
                .isEqualTo("Hello Alice!");
        assertThat(sim.<String, String>resolve("greeting.greet", "anyone"))
                .isEqualTo("Hello Bob!");
        assertThat(sim.<String, String>resolve("greeting.greet", "anyone"))
                .isEqualTo("Hello Alice!");
    }

    @Test
    void stubWithExplicitKeyAndExtractor() {
        var sim = Simulation.forTest()
                .stub("greeting.greet", "alice", "Alice", "Hello Alice!")
                .stub("greeting.greet", "bob", "Bob", "Hello Bob!")
                .keyExtractor("greeting.greet",
                        (String name) -> name.toLowerCase())
                .build();

        assertThat(sim.<String, String>resolve("greeting.greet", "ALICE"))
                .isEqualTo("Hello Alice!");
        assertThat(sim.<String, String>resolve("greeting.greet", "Bob"))
                .isEqualTo("Hello Bob!");
    }

    @Test
    void forTestDefaultTenancyId() {
        var sim = Simulation.forTest()
                .seed("spi.method", "in", "out")
                .build();

        assertThat(sim.<String, String>resolve("spi.method", "in"))
                .isEqualTo("out");
    }

    @Test
    void forTestCustomTenancyId() {
        var sim = Simulation.forTest("hospital-a")
                .seed("spi.method", "in", "out")
                .build();

        assertThat(sim.<String, String>resolve("spi.method", "in"))
                .isEqualTo("out");
    }

    @Test
    void mixedStubAndSeedOnSameQnThrows() {
        var builder = Simulation.forTest()
                .stub("spi.method", "key-in", "key-out");

        assertThatThrownBy(() -> builder.seed("spi.method", "seq-in", "seq-out"))
                .isInstanceOf(SimulationConfigException.class)
                .hasMessageContaining("Ambiguous strategy");
    }

    @Test
    void strategyExplicitOverride() {
        var sim = Simulation.forTest()
                .seed("spi.method", "a", "out-a")
                .seed("spi.method", "b", "out-b")
                .strategy("spi.method", "random")
                .build();

        String result = sim.<String, String>resolve("spi.method", "any");
        assertThat(result).isIn("out-a", "out-b");
    }

    @Test
    void multiMethodSimulation() {
        var sim = Simulation.forTest()
                .stub("spi.query", "patient-1", "result-1")
                .seed("spi.store", "input-a", "stored-a")
                .build();

        assertThat(sim.<String, String>resolve("spi.query", "patient-1"))
                .isEqualTo("result-1");
        assertThat(sim.<String, String>resolve("spi.store", "anything"))
                .isEqualTo("stored-a");
    }

    @Test
    void runtimeEscapeHatch() {
        var sim = Simulation.forTest()
                .seed("spi.method", "in", "out")
                .build();

        assertThat(sim.runtime()).isNotNull();
        assertThat(sim.runtime().strategyFor("spi.method")).isPresent();
    }

    @Test
    void resolveWithNoStrategyThrows() {
        var sim = Simulation.forTest()
                .seed("spi.method", "in", "out")
                .build();

        assertThatThrownBy(() -> sim.resolve("nonexistent.method", "input"))
                .isInstanceOf(SimulationConfigException.class)
                .hasMessageContaining("No strategy configured");
    }

    @Test
    void overlayAndVerifierLifecycle() {
        var sim = Simulation.forTest()
                .stub("spi.query", "patient-1", "result-1")
                .build();

        var overlay = sim.overlay();
        sim.resolve("spi.query", "patient-1");
        sim.resolve("spi.query", "patient-1");

        sim.verifier().method("spi.query").wasCalled(2);
        sim.popOverlay(overlay);
    }

    @Test
    void verifierWithoutOverlayThrows() {
        var sim = Simulation.forTest()
                .seed("spi.method", "in", "out")
                .build();

        assertThatThrownBy(sim::verifier)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active overlay");
    }

    @Test
    void popOverlayClearsCurrentOverlay() {
        var sim = Simulation.forTest()
                .seed("spi.method", "in", "out")
                .build();

        var overlay = sim.overlay();
        sim.popOverlay(overlay);

        assertThatThrownBy(sim::verifier)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active overlay");
    }

    @Test
    void overlayRecordsSimulatedCalls() {
        var sim = Simulation.forTest()
                .seed("spi.method", "in", "out")
                .build();

        var overlay = sim.overlay();
        sim.resolve("spi.method", "in");

        sim.verifier().method("spi.method").wasCalled(1);
        sim.verifier().method("spi.method").allSimulated();
        sim.popOverlay(overlay);
    }

    @Test
    void multiMethodVerification() {
        var sim = Simulation.forTest()
                .stub("acl.check", "admin", true)
                .seed("mem.store", "input", "stored")
                .build();

        var overlay = sim.overlay();
        sim.resolve("acl.check", "admin");
        sim.resolve("mem.store", "input");
        sim.resolve("mem.store", "input");

        sim.verifier().method("acl.check").wasCalled(1);
        sim.verifier().method("mem.store").wasCalled(2);
        sim.popOverlay(overlay);
    }
}
