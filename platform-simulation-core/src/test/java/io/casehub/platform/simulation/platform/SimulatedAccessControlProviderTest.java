package io.casehub.platform.simulation.platform;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.ResourceId;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.generated.SimulatedAccessControlProvider;
import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.simulation.ExhaustionPolicy;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedAccessControlProviderTest {

    private InMemorySimulationCorpus<Object, Object> corpus;
    private SimulatedAccessControlProvider decorator;

    @BeforeEach
    void setUp() throws Exception {
        corpus = new InMemorySimulationCorpus<>();

        final SimulationConfig config = new SimulationConfig() {
            @Override
            public Optional<String> strategyFor(final String qualifiedName) {
                if ("access-control-provider.canAccess".equals(qualifiedName)) {
                    return Optional.of("sequential");
                }
                return Optional.empty();
            }

            @Override
            public boolean captureEnabled(final String qualifiedName) {
                return false;
            }

            @Override
            public Optional<ExhaustionPolicy> exhaustionPolicy(final String qualifiedName) {
                return Optional.of(ExhaustionPolicy.THROW);
            }
        };

        final SimulationRuntime runtime = new SimulationRuntime(config, corpus);

        final CurrentPrincipal principal = new CurrentPrincipal() {
            @Override public String actorId() { return "test-actor"; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public String tenancyId() { return "test-tenant"; }
            @Override public boolean isCrossTenantAdmin() { return false; }
        };

        final AccessControlProvider noOpDelegate = new AccessControlProvider() {};

        decorator = new SimulatedAccessControlProvider();
        inject(decorator, "delegate", noOpDelegate);
        inject(decorator, "simulation", runtime);
        inject(decorator, "currentPrincipal", principal);
    }

    @Test
    void simulatedCanAccessOverridesDefaultBehavior() {
        corpus.seed("access-control-provider.canAccess", List.of(
                new InvocationRecord<>("test-tenant", null, null, false, Instant.now())));

        boolean result = decorator.canAccess("actor-1",
                ResourceId.parse("case:case-1"), AclAction.READ);
        assertThat(result).isFalse();
    }

    @Test
    void unconfiguredMethodDelegatesToDefault() {
        decorator.grant("actor-1", ResourceId.parse("case:case-1"),
                AclAction.WRITE, null);
    }

    @Test
    void passthroughWhenNoCorpusEntry() {
        boolean result = decorator.canAccess("actor-1",
                ResourceId.parse("case:case-1"), AclAction.READ);
        assertThat(result).isTrue();
    }

    private static void inject(final Object target, final String fieldName,
                                final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
