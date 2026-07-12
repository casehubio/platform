package io.casehub.platform.api.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UUIDv7Test {

    @Test
    void generate_wrapsSequenceByAdvancingTimestamp() {
        UUIDv7.resetState();
        Instant fixed = Instant.parse("2026-07-05T00:00:00Z");
        String first = UUIDv7.generate(fixed);
        String last = first;
        for (int i = 0; i < 4096; i++) {
            last = UUIDv7.generate(fixed);
        }
        // 4097th UUID must sort AFTER the 4096th — timestamp advanced
        assertThat(last.compareTo(first)).isGreaterThan(0);
    }

    @Test
    void generate_handlesClockRegression() {
        UUIDv7.resetState();
        Instant t1 = Instant.parse("2026-07-05T00:00:00.100Z");
        Instant t0 = Instant.parse("2026-07-05T00:00:00.099Z"); // earlier
        String a = UUIDv7.generate(t1);
        String b = UUIDv7.generate(t0); // clock went backwards
        assertThat(b.compareTo(a)).isGreaterThan(0); // must still be monotonic
    }
}
