package io.casehub.platform.authn.challenge.spring.jpa;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@AutoConfiguration
@EnableScheduling
public class ChallengeJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SpringChallengeStore springChallengeStore(ChallengeEntityRepository repo) {
        return new SpringChallengeStore(repo);
    }

    @Bean
    ChallengeExpiryPurgeTask challengeExpiryPurgeTask(SpringChallengeStore store) {
        return new ChallengeExpiryPurgeTask(store);
    }

    static class ChallengeExpiryPurgeTask {

        private final SpringChallengeStore store;

        ChallengeExpiryPurgeTask(SpringChallengeStore store) {
            this.store = store;
        }

        @Scheduled(fixedRate = 60_000)
        public void purgeExpired() {
            store.purgeExpired();
        }
    }
}
