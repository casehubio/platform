package io.casehub.platform.authn.inmem;

import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.ChallengeStore;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryChallengeStore implements ChallengeStore {

    private final ConcurrentHashMap<String, ChallengeRecord> challenges = new ConcurrentHashMap<>();

    @Override
    public void store(ChallengeRecord record) {
        challenges.put(record.challengeId(), record);
    }

    @Override
    public Optional<ChallengeRecord> consume(String challengeId) {
        return Optional.ofNullable(challenges.remove(challengeId));
    }

    public int size() {
        return challenges.size();
    }
}
