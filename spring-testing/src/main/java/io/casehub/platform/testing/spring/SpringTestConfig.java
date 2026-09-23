package io.casehub.platform.testing.spring;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot {@link TestConfiguration} providing platform test fixtures.
 *
 * <p>Import in test classes via {@code @Import(SpringTestConfig.class)} to get
 * a mutable {@link CurrentPrincipal}, {@link GroupMembershipProvider}, and
 * other platform SPI test doubles.
 */
@TestConfiguration
public class SpringTestConfig {

    @Bean
    public SpringFixedCurrentPrincipal currentPrincipal() {
        return new SpringFixedCurrentPrincipal();
    }

    @Bean
    public SpringInMemoryGroupMembershipProvider groupMembershipProvider() {
        return new SpringInMemoryGroupMembershipProvider();
    }
}
