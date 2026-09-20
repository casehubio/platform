package io.casehub.platform.simulation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemporalProfileTest {

    @Test
    void constructsWithAllFields() {
        var seq = new TimedSequence<>(List.of(
                new TimedEntry<>("A", Duration.ZERO, "step-a")));
        var profile = new TemporalProfile<>("test", "my.method", "tenant-1",
                seq, true, 10.0);

        assertThat(profile.name()).isEqualTo("test");
        assertThat(profile.qualifiedName()).isEqualTo("my.method");
        assertThat(profile.tenancyId()).isEqualTo("tenant-1");
        assertThat(profile.sequence().size()).isEqualTo(1);
        assertThat(profile.loop()).isTrue();
        assertThat(profile.speed()).isEqualTo(10.0);
    }

    @Test
    void rejectsNullName() {
        var seq = new TimedSequence<>(List.of());
        assertThatThrownBy(() -> new TemporalProfile<>(null, "qn", null, seq, false, 1.0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullQualifiedName() {
        var seq = new TimedSequence<>(List.of());
        assertThatThrownBy(() -> new TemporalProfile<>("n", null, null, seq, false, 1.0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullSequence() {
        assertThatThrownBy(() -> new TemporalProfile<>("n", "qn", null, null, false, 1.0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsZeroSpeed() {
        var seq = new TimedSequence<>(List.of());
        assertThatThrownBy(() -> new TemporalProfile<>("n", "qn", null, seq, false, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeSpeed() {
        var seq = new TimedSequence<>(List.of());
        assertThatThrownBy(() -> new TemporalProfile<>("n", "qn", null, seq, false, -1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsNullTenancyId() {
        var seq = new TimedSequence<>(List.of());
        var profile = new TemporalProfile<>("n", "qn", null, seq, false, 1.0);
        assertThat(profile.tenancyId()).isNull();
    }

    @Test
    void mapTransformsPayloadPreservesMetadata() {
        var seq = new TimedSequence<>(List.of(
                new TimedEntry<>("hello", Duration.ZERO, "step")));
        var profile = new TemporalProfile<>("test", "qn", "t1", seq, true, 5.0);

        var mapped = profile.map(String::length);

        assertThat(mapped.name()).isEqualTo("test");
        assertThat(mapped.qualifiedName()).isEqualTo("qn");
        assertThat(mapped.tenancyId()).isEqualTo("t1");
        assertThat(mapped.loop()).isTrue();
        assertThat(mapped.speed()).isEqualTo(5.0);
        assertThat(mapped.sequence().entries().get(0).event()).isEqualTo(5);
        assertThat(mapped.sequence().entries().get(0).label()).isEqualTo("step");
    }
}
