package io.casehub.platform.observability;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.ResourceId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentedAccessControlProviderTest {

    private SimpleMeterRegistry registry;
    private InstrumentedAccessControlProvider instrumented;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        AccessControlProvider delegate = new AccessControlProvider() {
            @Override
            public boolean canAccess(String actorId, ResourceId resourceId, AclAction action) {
                return "admin".equals(actorId);
            }
        };
        instrumented = new InstrumentedAccessControlProvider(delegate, registry);
    }

    @Test
    void canAccessIncrementsCounter() {
        instrumented.canAccess("admin", ResourceId.parse("case:123"), AclAction.READ);

        assertThat(registry.counter("casehub.platform.acl.can_access",
                "action", "READ").count()).isEqualTo(1);
    }

    @Test
    void canAccessRecordsTimer() {
        instrumented.canAccess("admin", ResourceId.parse("case:123"), AclAction.WRITE);

        assertThat(registry.timer("casehub.platform.acl.can_access.duration",
                "action", "WRITE").count()).isEqualTo(1);
    }

    @Test
    void canAccessDelegatesToProvider() {
        assertThat(instrumented.canAccess("admin", ResourceId.parse("case:1"), AclAction.READ)).isTrue();
        assertThat(instrumented.canAccess("user", ResourceId.parse("case:1"), AclAction.READ)).isFalse();
    }

    @Test
    void nonInstrumentedMethodsPassThrough() {
        instrumented.grant("actor", ResourceId.parse("case:1"), AclAction.READ, null);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getName()).doesNotContain("grant"));
    }
}
