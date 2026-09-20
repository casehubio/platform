package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.SimulationConfigException;
import io.casehub.platform.simulation.TemporalProfile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlTemporalProfileTest {

    @Test
    void parsesInlineEvents() {
        var yaml = """
                temporal-profiles:
                  morning-routine:
                    qualified-name: iot.device-state
                    tenancy-id: demo
                    loop: true
                    speed: 10.0
                    events:
                      - delay: 0
                        label: motion-start
                        payload:
                          deviceId: motion-01
                          state: ACTIVE
                      - delay: 5s
                        label: lights-on
                        payload:
                          deviceId: light-01
                          state: "ON"
                """;

        var config = parse(yaml);
        var profiles = config.temporalProfiles();
        assertThat(profiles).containsKey("morning-routine");

        var tp = profiles.get("morning-routine");
        assertThat(tp.qualifiedName()).isEqualTo("iot.device-state");
        assertThat(tp.tenancyId()).isEqualTo("demo");
        assertThat(tp.loop()).isTrue();
        assertThat(tp.speed()).isEqualTo(10.0);
        assertThat(tp.events()).hasSize(2);
        assertThat(tp.events().get(0).delay()).isEqualTo("0");
        assertThat(tp.events().get(0).label()).isEqualTo("motion-start");
        assertThat(tp.events().get(1).delay()).isEqualTo("5s");
    }

    @Test
    void resolvesTemporalProfile() {
        var yaml = """
                temporal-profiles:
                  test-profile:
                    qualified-name: my.method
                    events:
                      - delay: 0
                        label: step-a
                        payload:
                          key: value
                      - delay: 500ms
                        payload:
                          key: value2
                """;

        var config = parse(yaml);
        var profile = config.resolveTemporalProfile("test-profile");
        assertThat(profile).isPresent();

        TemporalProfile<Map<String, Object>> p = profile.get();
        assertThat(p.name()).isEqualTo("test-profile");
        assertThat(p.qualifiedName()).isEqualTo("my.method");
        assertThat(p.sequence().size()).isEqualTo(2);
        assertThat(p.sequence().entries().get(0).label()).isEqualTo("step-a");
        assertThat(p.sequence().entries().get(1).delay()).isEqualTo(Duration.ofMillis(500));
        assertThat(p.loop()).isFalse();
        assertThat(p.speed()).isEqualTo(1.0);
    }

    @Test
    void durationParsingVariants() {
        assertThat(DurationParser.parse("0")).isEqualTo(Duration.ZERO);
        assertThat(DurationParser.parse("500")).isEqualTo(Duration.ofMillis(500));
        assertThat(DurationParser.parse("500ms")).isEqualTo(Duration.ofMillis(500));
        assertThat(DurationParser.parse("5s")).isEqualTo(Duration.ofSeconds(5));
        assertThat(DurationParser.parse("2m")).isEqualTo(Duration.ofMinutes(2));
        assertThat(DurationParser.parse(null)).isEqualTo(Duration.ZERO);
        assertThat(DurationParser.parse("")).isEqualTo(Duration.ZERO);
    }

    @Test
    void mutualExclusivityRejectsMultipleSources() {
        assertThatThrownBy(() -> new TemporalProfileConfig(
                "qn", null, false, 1.0,
                List.of(new TemporalEventConfig("0", null, Map.of())),
                "classpath:file.yaml", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one source");
    }

    @Test
    void sequenceRefsResolve() {
        var yaml = """
                temporal-profiles:
                  step-a:
                    qualified-name: method.a
                    events:
                      - delay: 0
                        label: a1
                        payload: {key: a}
                  step-b:
                    qualified-name: method.b
                    events:
                      - delay: 0
                        label: b1
                        payload: {key: b}
                  combined:
                    qualified-name: method.combined
                    sequence:
                      - ref: step-a
                      - delay: 1s
                        ref: step-b
                """;

        var config = parse(yaml);
        var profile = config.resolveTemporalProfile("combined").get();

        assertThat(profile.sequence().size()).isEqualTo(2);
        assertThat(profile.sequence().entries().get(0).label()).isEqualTo("a1");
        assertThat(profile.sequence().entries().get(0).qualifiedName()).isEqualTo("method.a");
        assertThat(profile.sequence().entries().get(1).label()).isEqualTo("b1");
        assertThat(profile.sequence().entries().get(1).qualifiedName()).isEqualTo("method.b");
        assertThat(profile.sequence().entries().get(1).delay()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void circularRefThrows() {
        var yaml = """
                temporal-profiles:
                  a:
                    qualified-name: qn
                    sequence:
                      - ref: b
                  b:
                    qualified-name: qn
                    sequence:
                      - ref: a
                """;

        var config = parse(yaml);
        assertThatThrownBy(() -> config.resolveTemporalProfile("a"))
                .isInstanceOf(SimulationConfigException.class)
                .hasMessageContaining("Circular");
    }

    @Test
    void defaultSpeedIsOne() {
        var yaml = """
                temporal-profiles:
                  minimal:
                    qualified-name: qn
                    events:
                      - delay: 0
                        payload: {k: v}
                """;

        var config = parse(yaml);
        var profile = config.resolveTemporalProfile("minimal").get();
        assertThat(profile.speed()).isEqualTo(1.0);
    }

    @Test
    void temporalInProfilesInline() {
        var yaml = """
                profiles:
                  demo:
                    methods: {}
                    temporal:
                      - qualified-name: inline.method
                        events:
                          - delay: 0
                            payload: {k: v}
                """;

        var config = parse(yaml);
        var temporal = config.temporalForProfile("demo");
        assertThat(temporal).hasSize(1);
        assertThat(temporal.get(0).qualifiedName()).isEqualTo("inline.method");
    }

    @Test
    void temporalInProfilesRefs() {
        var yaml = """
                temporal-profiles:
                  reusable:
                    qualified-name: reusable.method
                    events:
                      - delay: 0
                        payload: {k: v}
                profiles:
                  demo:
                    methods: {}
                    temporal:
                      - ref: reusable
                """;

        var config = parse(yaml);
        var temporal = config.temporalForProfile("demo");
        assertThat(temporal).hasSize(1);
        assertThat(temporal.get(0).qualifiedName()).isEqualTo("reusable.method");
    }

    @Test
    void resolveNonExistentReturnsEmpty() {
        var config = parse("");
        assertThat(config.resolveTemporalProfile("nonexistent")).isEmpty();
    }

    private YamlSimulationConfig parse(String yaml) {
        return new YamlSimulationConfig(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
