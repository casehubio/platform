package io.casehub.platform.view.inmem;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
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
        var viewId    = UUID.randomUUID();
        tracker.updateMembership(subjectId, Map.of(viewId, "view-1"));

        assertThat(tracker.getLastKnownMembership(subjectId))
                .containsEntry(viewId, "view-1");
    }

    @Test
    void updateReplacePrevious() {
        var subjectId = UUID.randomUUID();
        var v1        = UUID.randomUUID();
        var v2        = UUID.randomUUID();
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

    @Test
    void bulkGetReturnsOnlyTrackedSubjects() {
        var s1        = UUID.randomUUID();
        var s2        = UUID.randomUUID();
        var untracked = UUID.randomUUID();
        var v1        = UUID.randomUUID();
        var v2        = UUID.randomUUID();
        tracker.updateMembership(s1, Map.of(v1, "view-1"));
        tracker.updateMembership(s2, Map.of(v2, "view-2"));

        var result = tracker.getLastKnownMembership(Set.of(s1, s2, untracked));

        assertThat(result).hasSize(2);
        assertThat(result.get(s1)).containsEntry(v1, "view-1");
        assertThat(result.get(s2)).containsEntry(v2, "view-2");
        assertThat(result).doesNotContainKey(untracked);
    }

    @Test
    void bulkGetEmptySetReturnsEmpty() {
        assertThat(tracker.getLastKnownMembership(Set.of())).isEmpty();
    }
}
