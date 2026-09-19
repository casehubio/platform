package io.casehub.platform.streams.kafka.spring;

import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.streams.kafka.KafkaStreamProcessorCore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaStreamSpringAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KafkaStreamSpringAutoConfiguration.class))
        .withBean(EndpointRegistry.class, () -> mock(EndpointRegistry.class))
        .withPropertyValues("casehub.streams.kafka.topic=test-topic");

    @Test
    void creates_kafka_stream_processor_core_bean() {
        runner.run(ctx ->
            assertThat(ctx).hasSingleBean(KafkaStreamProcessorCore.class));
    }

    @Test
    void backs_off_when_custom_bean_provided() {
        runner.withBean(KafkaStreamProcessorCore.class, () -> mock(KafkaStreamProcessorCore.class))
            .run(ctx ->
                assertThat(ctx).hasSingleBean(KafkaStreamProcessorCore.class));
    }

    @Test
    void not_created_without_topic_property() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaStreamSpringAutoConfiguration.class))
            .withBean(EndpointRegistry.class, () -> mock(EndpointRegistry.class))
            .run(ctx ->
                assertThat(ctx).doesNotHaveBean(KafkaStreamProcessorCore.class));
    }
}
