package io.casehub.platform.agent.gate.spring;

import io.casehub.platform.agent.gate.AgentGateProperties;
import io.casehub.platform.agent.gate.GatedAgentProviderWrapper;
import io.casehub.platform.agent.gate.SessionLeakReaper;
import io.casehub.platform.agent.gate.SessionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@AutoConfiguration
@AutoConfigureAfter(name = "io.casehub.platform.agent.router.spring.AgentRouterAutoConfiguration")
@ConditionalOnClass(GatedAgentProviderWrapper.class)
@EnableConfigurationProperties(AgentGateSpringProperties.class)
@EnableScheduling
public class AgentGateSpringAutoConfiguration {

    @Bean
    static AgentGateBeanPostProcessor agentGateBeanPostProcessor(
            AgentGateProperties properties, SessionRegistry registry) {
        return new AgentGateBeanPostProcessor(properties, registry);
    }

    @Bean
    SessionRegistry agentGateSessionRegistry() {
        return new SessionRegistry();
    }

    @Bean
    SessionLeakReaper sessionLeakReaper(SessionRegistry registry,
                                         AgentGateProperties properties) {
        var reaper = properties.reaper();
        return new SessionLeakReaper(registry,
                reaper.warnThreshold(), reaper.forceCloseEnabled(),
                reaper.forceCloseThreshold(), reaper.maxRegistryAge());
    }

    @Bean
    ReaperScheduler reaperScheduler(SessionLeakReaper reaper) {
        return new ReaperScheduler(reaper);
    }

    static class ReaperScheduler {
        private final SessionLeakReaper reaper;

        ReaperScheduler(SessionLeakReaper reaper) {
            this.reaper = reaper;
        }

        @Scheduled(fixedDelayString = "${casehub.platform.agent.gate.reaper.scan-interval:60000}")
        void scan() {
            reaper.scan();
        }
    }
}
