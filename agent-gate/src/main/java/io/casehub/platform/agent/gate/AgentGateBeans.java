package io.casehub.platform.agent.gate;

import io.casehub.platform.agent.AgentConcurrencyGate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class AgentGateBeans {

    @Produces
    @ApplicationScoped
    public SessionRegistry sessionRegistry() {
        return new SessionRegistry();
    }

    @Produces
    @ApplicationScoped
    public AgentConcurrencyGate concurrencyGate(AgentGateQuarkusProperties properties) {
        var cc = properties.concurrency();
        var tb = properties.tokenBucket();
        var sw = properties.slidingWindow();
        boolean active = cc.max() > 0 || tb.permitsPerSecond() > 0 || sw.maxActions() > 0;
        return () -> active;
    }
}
