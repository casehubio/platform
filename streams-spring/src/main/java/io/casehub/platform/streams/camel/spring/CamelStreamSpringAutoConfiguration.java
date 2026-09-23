package io.casehub.platform.streams.camel.spring;

import io.casehub.platform.api.endpoints.EndpointRegistered;
import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.streams.camel.CamelStreamProcessorCore;
import org.apache.camel.CamelContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

@AutoConfiguration
@ConditionalOnBean({EndpointRegistry.class, CamelContext.class})
public class CamelStreamSpringAutoConfiguration {

    private CamelStreamProcessorCore core;

    @Bean
    @ConditionalOnMissingBean
    CamelStreamProcessorCore camelStreamProcessorCore(CamelContext camelContext,
                                                      EndpointRegistry endpointRegistry,
                                                      ApplicationEventPublisher publisher) {
        core = new CamelStreamProcessorCore(camelContext, endpointRegistry,
            ce -> publisher.publishEvent(ce));
        return core;
    }

    @EventListener(ApplicationStartedEvent.class)
    void onStartup() {
        if (core != null) core.init();
    }

    @EventListener
    void onEndpointRegistered(EndpointRegistered event) {
        if (core != null) core.onEndpointRegistered(event.descriptor());
    }
}
