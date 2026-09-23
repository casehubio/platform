package io.casehub.platform.streams.kafka.spring;

import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.streams.kafka.KafkaStreamProcessorCore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

import java.util.Map;

@AutoConfiguration
@ConditionalOnBean(EndpointRegistry.class)
@ConditionalOnProperty("casehub.streams.kafka.topic")
public class KafkaStreamSpringAutoConfiguration {

    private KafkaStreamProcessorCore core;

    @Bean
    @ConditionalOnMissingBean
    KafkaStreamProcessorCore kafkaStreamProcessorCore(
            EndpointRegistry endpointRegistry,
            ApplicationEventPublisher publisher,
            @Value("${casehub.streams.kafka.topic}") String topic) {
        core = new KafkaStreamProcessorCore(endpointRegistry,
            ce -> publisher.publishEvent(ce),
            Map.of("default", topic));
        return core;
    }

    @EventListener(ApplicationStartedEvent.class)
    void onStartup() {
        if (core != null) core.init();
    }

    @KafkaListener(topics = "${casehub.streams.kafka.topic}",
                   autoStartup = "${casehub.streams.kafka.enabled:true}")
    void handleMessage(@Payload byte[] payload,
                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                       @Header(name = "X-Tenancy-ID", required = false) String tenancyId) {
        if (core != null) core.processMessage(payload, topic, tenancyId);
    }
}
