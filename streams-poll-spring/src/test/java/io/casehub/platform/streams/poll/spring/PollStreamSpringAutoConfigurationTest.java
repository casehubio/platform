package io.casehub.platform.streams.poll.spring;

import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.streams.poll.PollStreamProcessorCore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PollStreamSpringAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PollStreamSpringAutoConfiguration.class))
        .withBean(EndpointRegistry.class, () -> mock(EndpointRegistry.class));

    @Test
    void creates_poll_stream_processor_core_bean() {
        runner.run(ctx ->
            assertThat(ctx).hasSingleBean(PollStreamProcessorCore.class));
    }

    @Test
    void backs_off_when_custom_bean_provided() {
        runner.withBean(PollStreamProcessorCore.class, () -> mock(PollStreamProcessorCore.class))
            .run(ctx ->
                assertThat(ctx).hasSingleBean(PollStreamProcessorCore.class));
    }

    @Test
    void not_created_without_endpoint_registry() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PollStreamSpringAutoConfiguration.class))
            .run(ctx ->
                assertThat(ctx).doesNotHaveBean(PollStreamProcessorCore.class));
    }
}
