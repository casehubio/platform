package io.casehub.platform.view.jpa;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.quarkus.test.TestTransaction;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestTransaction
class JpaViewMembershipTrackerTest {

    @Inject
    JpaViewMembershipTracker tracker;

    @Test
    void getUnknownReturnsEmpty() {
        assertThat(tracker.getLastKnownMembership(UUID.randomUUID())).isEmpty();
    }

    @Test
    void updateAndGet() {
        var subjectId = UUID.randomUUID();
        var viewId = UUID.randomUUID();
        tracker.updateMembership(subjectId, Map.of(viewId, "view-1"));

        var result = tracker.getLastKnownMembership(subjectId);
        assertThat(result).containsEntry(viewId, "view-1");
    }

    @Test
    void updateReplacesPrevious() {
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
    void remove() {
        var subjectId = UUID.randomUUID();
        tracker.updateMembership(subjectId, Map.of(UUID.randomUUID(), "v"));
        tracker.removeMembership(subjectId);
        assertThat(tracker.getLastKnownMembership(subjectId)).isEmpty();
    }
}
