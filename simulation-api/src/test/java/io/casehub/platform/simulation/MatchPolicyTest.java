package io.casehub.platform.simulation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchPolicyTest {

    @Test
    void hasExpectedValues() {
        assertThat(MatchPolicy.values())
                .containsExactly(MatchPolicy.FIRST, MatchPolicy.RANDOM, MatchPolicy.SEQUENTIAL);
    }
}
