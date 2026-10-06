package io.casehub.platform.authn;

import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.ChallengeStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpChallengeStore implements ChallengeStore {

    @Override
    public void store(ChallengeRecord record) {}

    @Override
    public Optional<ChallengeRecord> consume(String challengeId) {
        return Optional.empty();
    }
}
