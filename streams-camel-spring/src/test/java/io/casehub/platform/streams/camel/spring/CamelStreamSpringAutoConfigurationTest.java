package io.casehub.platform.streams.camel.spring;

import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.streams.camel.CamelStreamProcessorCore;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CamelStreamSpringAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CamelStreamSpringAutoConfiguration.class))
        .withBean(EndpointRegistry.class, () -> mock(EndpointRegistry.class))
        .withBean(CamelContext.class, DefaultCamelContext::new);

    @Test
    void creates_camel_stream_processor_core_bean() {
        runner.run(ctx ->
            assertThat(ctx).hasSingleBean(CamelStreamProcessorCore.class));
    }

    @Test
    void backs_off_when_custom_bean_provided() {
        runner.withBean(CamelStreamProcessorCore.class, () -> mock(CamelStreamProcessorCore.class))
            .run(ctx ->
                assertThat(ctx).hasSingleBean(CamelStreamProcessorCore.class));
    }

    @Test
    void not_created_without_camel_context() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CamelStreamSpringAutoConfiguration.class))
            .withBean(EndpointRegistry.class, () -> mock(EndpointRegistry.class))
            .run(ctx ->
                assertThat(ctx).doesNotHaveBean(CamelStreamProcessorCore.class));
    }
}
