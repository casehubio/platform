package io.casehub.platform.view.inmem;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryViewMembershipTrackerTest {

    private final InMemoryViewMembershipTracker tracker = new InMemoryViewMembershipTracker();

    @Test
    void getLastKnownMembershipReturnsEmptyForUnknown() {
        assertThat(tracker.getLastKnownMembership(UUID.randomUUID())).isEmpty();
    }

    @Test
    void updateThenGet() {
        var subjectId = UUID.randomUUID();
        var viewId = UUID.randomUUID();
        tracker.updateMembership(subjectId, Map.of(viewId, "view-1"));

        assertThat(tracker.getLastKnownMembership(subjectId))
            .containsEntry(viewId, "view-1");
    }

    @Test
    void updateReplacePrevious() {
        var subjectId = UUID.randomUUID();
        var v1 = UUID.randomUUID();
        var v2 = UUID.randomUUID();
        tracker.updateMembership(subjectId, Map.of(v1, "old"));
        tracker.updateMembership(subjectId, Map.of(v2, "new"));

        var result = tracker.getLastKnownMembership(subjectId);
        assertThat(result).doesNotContainKey(v1);
        assertThat(result).containsEntry(v2, "new");
    }

    @Test
    void removeMembership() {
        var subjectId = UUID.randomUUID();
        tracker.updateMembership(subjectId, Map.of(UUID.randomUUID(), "v"));
        tracker.removeMembership(subjectId);
        assertThat(tracker.getLastKnownMembership(subjectId)).isEmpty();
    }

    @Test
    void removeMembershipNonExistentDoesNotThrow() {
        tracker.removeMembership(UUID.randomUUID());
    }

    @Test
    void returnedMapIsDefensiveCopy() {
        var subjectId = UUID.randomUUID();
        tracker.updateMembership(subjectId, Map.of(UUID.randomUUID(), "v"));

        var a = tracker.getLastKnownMembership(subjectId);
        var b = tracker.getLastKnownMembership(subjectId);
        assertThat(a).isNotSameAs(b);
    }
}
