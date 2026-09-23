package io.casehub.platform.streams.poll.spring;

import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.streams.poll.PollStreamProcessorCore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@AutoConfiguration
@EnableScheduling
@ConditionalOnBean(EndpointRegistry.class)
public class PollStreamSpringAutoConfiguration {

    private PollStreamProcessorCore core;

    @Bean
    @ConditionalOnMissingBean
    PollStreamProcessorCore pollStreamProcessorCore(EndpointRegistry endpointRegistry,
                                                    ApplicationEventPublisher publisher) {
        core = new PollStreamProcessorCore(endpointRegistry,
            ce -> publisher.publishEvent(ce));
        return core;
    }

    @Scheduled(fixedDelayString = "${casehub.streams.poll.interval:60000}")
    void poll() {
        if (core != null) core.poll();
    }
}
