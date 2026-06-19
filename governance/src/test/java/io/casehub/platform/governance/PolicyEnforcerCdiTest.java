package io.casehub.platform.governance;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class PolicyEnforcerCdiTest {

    @Inject
    PolicyEnforcer enforcer;

    @Test
    void policyEnforcer_isInjectable() {
        assertThat(enforcer).isNotNull();
        assertThat(enforcer).isInstanceOf(DefaultPolicyEnforcer.class);
    }
}
