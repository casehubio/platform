package io.casehub.platform.capacity;

import io.casehub.platform.api.capacity.CapacitySignal;
import io.casehub.platform.api.capacity.CapacitySignalSource;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AggregatingActorCapacityViewTest {

    @Test
    void no_sources_returns_zero_pressure() {
        var view = new AggregatingActorCapacityView(List.of());
        view.refresh();
        var result = view.aggregatedPressure("actor-1");
        assertThat(result.pressure()).isEqualTo(0.0);
        assertThat(result.source()).isEqualTo("aggregated");
    }

    @Test
    void single_source_returns_its_pressure() {
        CapacitySignalSource source = new CapacitySignalSource() {
            @Override public String sourceName() { return "work-queue"; }
            @Override public List<CapacitySignal> signals() {
                return List.of(new CapacitySignal("actor-1", "work-queue", 0.6, Instant.now()));
            }
        };
        var view = new AggregatingActorCapacityView(List.of(source));
        view.refresh();
        assertThat(view.aggregatedPressure("actor-1").pressure()).isEqualTo(0.6);
    }

    @Test
    void max_pressure_across_sources() {
        CapacitySignalSource s1 = new CapacitySignalSource() {
            @Override public String sourceName() { return "s1"; }
            @Override public List<CapacitySignal> signals() {
                return List.of(new CapacitySignal("actor-1", "s1", 0.4, Instant.now()));
            }
        };
        CapacitySignalSource s2 = new CapacitySignalSource() {
            @Override public String sourceName() { return "s2"; }
            @Override public List<CapacitySignal> signals() {
                return List.of(new CapacitySignal("actor-1", "s2", 0.8, Instant.now()));
            }
        };
        var view = new AggregatingActorCapacityView(List.of(s1, s2));
        view.refresh();
        assertThat(view.aggregatedPressure("actor-1").pressure()).isEqualTo(0.8);
    }

    @Test
    void multiple_actors_grouped() {
        CapacitySignalSource source = new CapacitySignalSource() {
            @Override public String sourceName() { return "s"; }
            @Override public List<CapacitySignal> signals() {
                return List.of(
                        new CapacitySignal("a", "s", 0.3, Instant.now()),
                        new CapacitySignal("b", "s", 0.7, Instant.now()));
            }
        };
        var view = new AggregatingActorCapacityView(List.of(source));
        view.refresh();
        assertThat(view.aggregatedPressure("a").pressure()).isEqualTo(0.3);
        assertThat(view.aggregatedPressure("b").pressure()).isEqualTo(0.7);
    }

    @Test
    void signals_by_actor_returns_individual_sources() {
        CapacitySignalSource s1 = new CapacitySignalSource() {
            @Override public String sourceName() { return "s1"; }
            @Override public List<CapacitySignal> signals() {
                return List.of(new CapacitySignal("a", "s1", 0.3, Instant.now()));
            }
        };
        CapacitySignalSource s2 = new CapacitySignalSource() {
            @Override public String sourceName() { return "s2"; }
            @Override public List<CapacitySignal> signals() {
                return List.of(new CapacitySignal("a", "s2", 0.6, Instant.now()));
            }
        };
        var view = new AggregatingActorCapacityView(List.of(s1, s2));
        view.refresh();
        assertThat(view.signalsByActor("a")).hasSize(2);
    }

    @Test
    void refresh_replaces_cache() {
        var mutablePressure = new double[]{0.5};
        CapacitySignalSource source = new CapacitySignalSource() {
            @Override public String sourceName() { return "s"; }
            @Override public List<CapacitySignal> signals() {
                return List.of(new CapacitySignal("a", "s", mutablePressure[0], Instant.now()));
            }
        };
        var view = new AggregatingActorCapacityView(List.of(source));
        view.refresh();
        assertThat(view.aggregatedPressure("a").pressure()).isEqualTo(0.5);

        mutablePressure[0] = 0.9;
        view.refresh();
        assertThat(view.aggregatedPressure("a").pressure()).isEqualTo(0.9);
    }

    @Test
    void all_aggregated_pressures() {
        CapacitySignalSource source = new CapacitySignalSource() {
            @Override public String sourceName() { return "s"; }
            @Override public List<CapacitySignal> signals() {
                return List.of(
                        new CapacitySignal("a", "s", 0.3, Instant.now()),
                        new CapacitySignal("b", "s", 0.7, Instant.now()));
            }
        };
        var view = new AggregatingActorCapacityView(List.of(source));
        view.refresh();
        assertThat(view.allAggregatedPressures()).hasSize(2);
    }
}
