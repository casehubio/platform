package io.casehub.platform.delivery.digest.spring.jpa;

import io.casehub.platform.api.delivery.DigestBuffer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringDigestBuffer.class)
@EnableJpaRepositories(basePackageClasses = DigestBufferEntityRepository.class)
public class DigestJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DigestBuffer.class)
    public SpringDigestBuffer springDigestBuffer(
            DigestBufferEntityRepository repo,
            @Value("${casehub.notification.digest.max-buffer-size:0}") int maxBufferSize) {
        return new SpringDigestBuffer(repo, maxBufferSize);
    }
}
