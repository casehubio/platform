package io.casehub.platform.api.registry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CascadeRuleTest {

    @Test
    void fullConstructorSetsAllFields() {
        var rule = new CascadeRule("owns", CascadeAction.DEREGISTER,
                CascadeAction.NOTIFY, Duration.ofSeconds(30));
        assertThat(rule.relationshipType()).isEqualTo("owns");
        assertThat(rule.onSourceDeregister()).isEqualTo(CascadeAction.DEREGISTER);
        assertThat(rule.onSourceExpired()).isEqualTo(CascadeAction.NOTIFY);
        assertThat(rule.cascadeGracePeriod()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void convenienceConstructorDefaultsNullableFields() {
        var rule = new CascadeRule("owns", CascadeAction.DEREGISTER);
        assertThat(rule.onSourceExpired()).isNull();
        assertThat(rule.cascadeGracePeriod()).isNull();
    }

    @Test
    void effectiveOnSourceExpiredFallsBackToDeregister() {
        var rule = new CascadeRule("owns", CascadeAction.DEREGISTER);
        assertThat(rule.effectiveOnSourceExpired()).isEqualTo(CascadeAction.DEREGISTER);
    }

    @Test
    void effectiveOnSourceExpiredUsesExplicitValue() {
        var rule = new CascadeRule("owns", CascadeAction.DEREGISTER,
                CascadeAction.NOTIFY, null);
        assertThat(rule.effectiveOnSourceExpired()).isEqualTo(CascadeAction.NOTIFY);
    }

    @Test
    void nullRelationshipTypeThrows() {
        assertThatThrownBy(() -> new CascadeRule(null, CascadeAction.DEREGISTER))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullOnSourceDeregisterThrows() {
        assertThatThrownBy(() -> new CascadeRule("owns", null))
                .isInstanceOf(NullPointerException.class);
    }
}
