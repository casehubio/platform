package io.casehub.platform.view;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpViewMembershipTrackerTest {

    private final NoOpViewMembershipTracker tracker = new NoOpViewMembershipTracker();

    @Test
    void getLastKnownMembershipReturnsEmpty() {
        assertThat(tracker.getLastKnownMembership(UUID.randomUUID())).isEmpty();
    }

    @Test
    void updateMembershipDoesNotThrow() {
        tracker.updateMembership(UUID.randomUUID(), Map.of(UUID.randomUUID(), "view"));
    }

    @Test
    void removeMembershipDoesNotThrow() {
        tracker.removeMembership(UUID.randomUUID());
    }
}
