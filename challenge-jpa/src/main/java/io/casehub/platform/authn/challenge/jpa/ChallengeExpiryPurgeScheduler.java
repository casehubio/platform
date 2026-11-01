package io.casehub.platform.authn.challenge.jpa;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.logging.Logger;

@ApplicationScoped
public class ChallengeExpiryPurgeScheduler {

    private static final Logger LOG = Logger.getLogger(ChallengeExpiryPurgeScheduler.class.getName());

    @Inject
    JpaChallengeStore challengeStore;

    @Scheduled(every = "60s")
    void purgeExpiredChallenges() {
        int purged = challengeStore.purgeExpired();
        if (purged > 0) {
            LOG.fine("Purged " + purged + " expired challenges");
        }
    }
}
