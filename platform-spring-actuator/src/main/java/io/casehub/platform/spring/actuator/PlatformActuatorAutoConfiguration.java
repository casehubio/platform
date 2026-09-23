package io.casehub.platform.spring.actuator;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import io.casehub.platform.api.model.ModelRegistry;
import io.casehub.platform.api.model.MutableModelRegistry;
import io.casehub.platform.observability.KeyStoreExpiryChecker;
import io.casehub.platform.observability.PlatformGaugeBinder;
import io.casehub.platform.scim.ScimClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@ConditionalOnClass(HealthIndicator.class)
@EnableConfigurationProperties(CertificateExpiryProperties.class)
public class PlatformActuatorAutoConfiguration {

    @Bean
    static MetricsBeanPostProcessor platformMetricsBeanPostProcessor(MeterRegistry meterRegistry) {
        return new MetricsBeanPostProcessor(meterRegistry);
    }

    @Bean
    PlatformGaugeBinder platformGaugeBinder(
            ObjectProvider<ModelRegistry> modelRegistry,
            ObjectProvider<DeliveryChannelRegistry> channelRegistry,
            ObjectProvider<List<AgentBackend>> backends) {
        return new PlatformGaugeBinder(
                modelRegistry.getIfAvailable(),
                channelRegistry.getIfAvailable(),
                backends.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean(MutableModelRegistry.class)
    ModelRegistryHealthIndicator modelRegistryHealthIndicator(MutableModelRegistry registry) {
        return new ModelRegistryHealthIndicator(registry);
    }

    @Bean
    @ConditionalOnBean(AgentBackend.class)
    AgentBackendHealthIndicator agentBackendHealthIndicator(List<AgentBackend> backends) {
        return new AgentBackendHealthIndicator(backends);
    }

    @Bean
    @ConditionalOnBean(DeliveryChannelRegistry.class)
    DeliveryChannelHealthIndicator deliveryChannelHealthIndicator(DeliveryChannelRegistry registry) {
        return new DeliveryChannelHealthIndicator(registry);
    }

    @Bean
    @ConditionalOnBean(ScimClient.class)
    ScimHealthIndicator scimHealthIndicator(ScimClient client) {
        return new ScimHealthIndicator(client);
    }

    @Bean
    @ConditionalOnProperty("casehub.signing.keystore-path")
    KeyStoreExpiryChecker keyStoreExpiryChecker(CertificateExpiryProperties props) {
        return new KeyStoreExpiryChecker(
                props.keystorePath(),
                props.keystorePassword() != null ? props.keystorePassword().toCharArray() : null,
                props.keystoreType(),
                props.expiryWarningDays());
    }

    @Bean
    @ConditionalOnBean(KeyStoreExpiryChecker.class)
    CertificateExpiryHealthIndicator certificateExpiryHealthIndicator(KeyStoreExpiryChecker checker) {
        return new CertificateExpiryHealthIndicator(checker);
    }

    @Bean
    PlatformInfoContributor platformInfoContributor(
            ObjectProvider<ModelRegistry> modelRegistry,
            ObjectProvider<List<AgentBackend>> backends) {
        return new PlatformInfoContributor(
                modelRegistry.getIfAvailable(),
                backends.getIfAvailable());
    }
}
