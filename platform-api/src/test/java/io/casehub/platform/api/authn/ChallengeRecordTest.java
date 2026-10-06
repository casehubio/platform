package io.casehub.platform.api.authn;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class ChallengeRecordTest {

    @Test
    void constructor_rejects_null_challengeId() {
        assertThrows(NullPointerException.class,
            () -> new ChallengeRecord(null, "webauthn", "tenant", new byte[32], Instant.now(), Instant.now().plusSeconds(300)));
    }

    @Test
    void constructor_rejects_null_method() {
        assertThrows(NullPointerException.class,
            () -> new ChallengeRecord("ch-1", null, "tenant", new byte[32], Instant.now(), Instant.now().plusSeconds(300)));
    }

    @Test
    void constructor_rejects_null_tenancyId() {
        assertThrows(NullPointerException.class,
            () -> new ChallengeRecord("ch-1", "webauthn", null, new byte[32], Instant.now(), Instant.now().plusSeconds(300)));
    }

    @Test
    void challengeData_is_defensively_copied() {
        byte[] original = {1, 2, 3};
        var record = new ChallengeRecord("ch-1", "webauthn", "tenant", original, Instant.now(), Instant.now().plusSeconds(300));
        original[0] = 99;
        assertEquals(1, record.challengeData()[0]);
    }

    @Test
    void challengeData_accessor_returns_copy() {
        byte[] original = {1, 2, 3};
        var record = new ChallengeRecord("ch-1", "webauthn", "tenant", original, Instant.now(), Instant.now().plusSeconds(300));
        byte[] first = record.challengeData();
        first[0] = 99;
        assertEquals(1, record.challengeData()[0]);
    }

    @Test
    void null_challengeData_is_allowed() {
        var record = new ChallengeRecord("ch-1", "webauthn", "tenant", null, Instant.now(), Instant.now().plusSeconds(300));
        assertNull(record.challengeData());
    }
}
