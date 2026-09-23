package io.casehub.platform.spring.actuator;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.observability.InstrumentedAgentBackend;
import io.casehub.platform.observability.InstrumentedAccessControlProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

class MetricsBeanPostProcessor implements BeanPostProcessor, Ordered {

    private final MeterRegistry meterRegistry;

    MetricsBeanPostProcessor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public int getOrder() { return 1900; }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AgentBackend backend
                && !(bean instanceof InstrumentedAgentBackend)) {
            return new InstrumentedAgentBackend(backend, meterRegistry);
        }
        if (bean instanceof AccessControlProvider provider
                && !(bean instanceof InstrumentedAccessControlProvider)) {
            return new InstrumentedAccessControlProvider(provider, meterRegistry);
        }
        return bean;
    }
}
