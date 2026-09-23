package io.casehub.platform.streams.amqp.spring;

import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.streams.amqp.AmqpStreamProcessorCore;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

import java.util.Map;

@AutoConfiguration
@ConditionalOnBean(EndpointRegistry.class)
@ConditionalOnProperty("casehub.streams.amqp.queue")
public class AmqpStreamSpringAutoConfiguration {

    private AmqpStreamProcessorCore core;

    @Bean
    @ConditionalOnMissingBean
    AmqpStreamProcessorCore amqpStreamProcessorCore(
            EndpointRegistry endpointRegistry,
            ApplicationEventPublisher publisher,
            @Value("${casehub.streams.amqp.queue}") String queue) {
        core = new AmqpStreamProcessorCore(endpointRegistry,
            ce -> publisher.publishEvent(ce),
            Map.of("default", queue));
        return core;
    }

    @EventListener(ApplicationStartedEvent.class)
    void onStartup() {
        if (core != null) core.init();
    }

    @RabbitListener(queues = "${casehub.streams.amqp.queue}",
                    autoStartup = "${casehub.streams.amqp.enabled:true}")
    void handleMessage(Message message) {
        if (core == null) return;
        String address = message.getMessageProperties().getConsumerQueue();
        String tenancyId = message.getMessageProperties().getHeader("X-Tenancy-ID");
        core.processMessage(message.getBody(), address != null ? address : "unknown", tenancyId);
    }
}
