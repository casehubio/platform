package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.CorpusSelectorConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlCorpusSelectionTest {

    @Test
    void parsesStateMachineCorpusSelection() {
        var yaml = """
                methods:
                  test-spi.query:
                    strategy: key-lookup
                    corpus-selection:
                      type: state-machine
                      initial: pending
                      transitions:
                        pending:
                          pay: completed
                      phases:
                        pending:
                          - key: k1
                            output: pending-result
                        completed:
                          - key: k1
                            output: completed-result
                """;
        var config = parse(yaml);

        var selection = config.corpusSelection("test-spi.query");
        assertThat(selection).isPresent();
        assertThat(selection.get()).isInstanceOf(CorpusSelectorConfig.StateMachine.class);

        var sm = (CorpusSelectorConfig.StateMachine) selection.get();
        assertThat(sm.initial()).isEqualTo("pending");
        assertThat(sm.transitions()).containsKey("pending");
        assertThat(sm.phases()).containsKeys("pending", "completed");
        assertThat(sm.phases().get("pending")).hasSize(1);
        assertThat(sm.phases().get("pending").get(0).key()).isEqualTo("k1");
        assertThat(sm.phases().get("pending").get(0).output()).isEqualTo("pending-result");
    }

    @Test
    void parsesDurationCorpusSelection() {
        var yaml = """
                methods:
                  test-spi.query:
                    strategy: sequential
                    corpus-selection:
                      type: duration
                      phases:
                        - name: normal
                          duration: 10s
                          corpus:
                            - output: normal-result
                        - name: degraded
                          corpus:
                            - output: degraded-result
                """;
        var config = parse(yaml);

        var selection = config.corpusSelection("test-spi.query");
        assertThat(selection).isPresent();
        assertThat(selection.get()).isInstanceOf(CorpusSelectorConfig.DurationBased.class);

        var d = (CorpusSelectorConfig.DurationBased) selection.get();
        assertThat(d.orderedPhases()).hasSize(2);
        assertThat(d.orderedPhases().get(0).duration().getSeconds()).isEqualTo(10);
        assertThat(d.orderedPhases().get(1).duration()).isNull();
        assertThat(d.phases()).containsKeys("normal", "degraded");
    }

    @Test
    void parsesLookupCountCorpusSelection() {
        var yaml = """
                methods:
                  test-spi.query:
                    strategy: sequential
                    corpus-selection:
                      type: lookup-count
                      phases:
                        - name: pending
                          count: 3
                          corpus:
                            - output: pending-result
                        - name: completed
                          corpus:
                            - output: completed-result
                """;
        var config = parse(yaml);

        var selection = config.corpusSelection("test-spi.query");
        assertThat(selection).isPresent();
        assertThat(selection.get()).isInstanceOf(CorpusSelectorConfig.LookupCount.class);

        var lc = (CorpusSelectorConfig.LookupCount) selection.get();
        assertThat(lc.orderedPhases()).hasSize(2);
        assertThat(lc.orderedPhases().get(0).count()).isEqualTo(3);
        assertThat(lc.phases()).containsKeys("pending", "completed");
    }

    @Test
    void parsesRandomCorpusSelection() {
        var yaml = """
                methods:
                  test-spi.query:
                    strategy: key-lookup
                    corpus-selection:
                      type: random
                      phases:
                        - name: normal
                          weight: 0.8
                          corpus:
                            - key: k1
                              output: ok
                        - name: error
                          weight: 0.2
                          corpus:
                            - key: k1
                              output: fail
                """;
        var config = parse(yaml);

        var selection = config.corpusSelection("test-spi.query");
        assertThat(selection).isPresent();
        assertThat(selection.get()).isInstanceOf(CorpusSelectorConfig.Weighted.class);

        var w = (CorpusSelectorConfig.Weighted) selection.get();
        assertThat(w.weightedPhases()).hasSize(2);
        assertThat(w.weightedPhases().get(0).weight()).isEqualTo(0.8);
        assertThat(w.phases()).containsKeys("normal", "error");
    }

    @Test
    void corpusAndCorpusSelectionAreMutuallyExclusive() {
        var yaml = """
                methods:
                  test-spi.query:
                    strategy: sequential
                    corpus:
                      - output: static
                    corpus-selection:
                      type: duration
                      phases:
                        - name: a
                          corpus:
                            - output: phased
                """;
        assertThatThrownBy(() -> parse(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    void corpusFilesAndCorpusSelectionAreMutuallyExclusive() {
        var yaml = """
                methods:
                  test-spi.query:
                    strategy: sequential
                    corpus-files:
                      - classpath:test.yaml
                    corpus-selection:
                      type: duration
                      phases:
                        - name: a
                          corpus:
                            - output: phased
                """;
        assertThatThrownBy(() -> parse(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    void noCorpusSelectionReturnsEmpty() {
        var yaml = """
                methods:
                  test-spi.query:
                    strategy: sequential
                    corpus:
                      - output: static
                """;
        var config = parse(yaml);
        assertThat(config.corpusSelection("test-spi.query")).isEmpty();
    }

    @Test
    void unknownTypeThrows() {
        var yaml = """
                methods:
                  test-spi.query:
                    strategy: sequential
                    corpus-selection:
                      type: unknown
                      phases:
                        - name: a
                          corpus:
                            - output: x
                """;
        assertThatThrownBy(() -> parse(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void stateMachinePhaseEntriesCarryTenancyId() {
        var yaml = """
                default-tenancy-id: default-tenant
                methods:
                  test-spi.query:
                    strategy: key-lookup
                    corpus-selection:
                      type: state-machine
                      initial: a
                      transitions: {}
                      phases:
                        a:
                          - key: k1
                            tenancy-id: custom-tenant
                            output: result
                """;
        var config = parse(yaml);
        var sm = (CorpusSelectorConfig.StateMachine) config.corpusSelection("test-spi.query").get();
        assertThat(sm.phases().get("a").get(0).tenancyId()).isEqualTo("custom-tenant");
    }


    @Test
    void parsesTopLevelTriggers() {
        var yaml = """
                   triggers:
                     pressure-high:
                       event-type: "capacity.pressure"
                       condition: "pressure > 0.8"
                     outage-start:
                       event-type: "service.health"
                   methods:
                     test-spi.query:
                       strategy: key-lookup
                       corpus-selection:
                         type: state-machine
                         initial: normal
                         transitions:
                           normal:
                             pressure-high: degraded
                         phases:
                           normal:
                             - key: k1
                               output: normal-result
                           degraded:
                             - key: k1
                               output: degraded-result
                   """;
        var config = parse(yaml);

        assertThat(config.triggers()).hasSize(2);
        assertThat(config.triggers().get("pressure-high").eventType()).isEqualTo("capacity.pressure");
        assertThat(config.triggers().get("pressure-high").condition()).isEqualTo("pressure > 0.8");
        assertThat(config.triggers().get("outage-start").eventType()).isEqualTo("service.health");
        assertThat(config.triggers().get("outage-start").condition()).isNull();
    }

    @Test
    void noTriggersReturnsEmptyMap() {
        var yaml = """
                   methods:
                     test-spi.query:
                       strategy: sequential
                       corpus:
                         - output: static
                   """;
        var config = parse(yaml);
        assertThat(config.triggers()).isEmpty();
    }


    private YamlSimulationConfig parse(String yaml) {
        return new YamlSimulationConfig(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
