package io.casehub.platform.streams.amqp.spring;

import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.streams.amqp.AmqpStreamProcessorCore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AmqpStreamSpringAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AmqpStreamSpringAutoConfiguration.class))
        .withBean(EndpointRegistry.class, () -> mock(EndpointRegistry.class))
        .withPropertyValues("casehub.streams.amqp.queue=test-queue");

    @Test
    void creates_amqp_stream_processor_core_bean() {
        runner.run(ctx ->
            assertThat(ctx).hasSingleBean(AmqpStreamProcessorCore.class));
    }

    @Test
    void backs_off_when_custom_bean_provided() {
        runner.withBean(AmqpStreamProcessorCore.class, () -> mock(AmqpStreamProcessorCore.class))
            .run(ctx ->
                assertThat(ctx).hasSingleBean(AmqpStreamProcessorCore.class));
    }

    @Test
    void not_created_without_queue_property() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AmqpStreamSpringAutoConfiguration.class))
            .withBean(EndpointRegistry.class, () -> mock(EndpointRegistry.class))
            .run(ctx ->
                assertThat(ctx).doesNotHaveBean(AmqpStreamProcessorCore.class));
    }
}
