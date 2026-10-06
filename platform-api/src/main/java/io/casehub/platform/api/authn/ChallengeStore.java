package io.casehub.platform.api.authn;

import java.util.Optional;

public interface ChallengeStore {
    void store(ChallengeRecord record);
    Optional<ChallengeRecord> consume(String challengeId);
}
